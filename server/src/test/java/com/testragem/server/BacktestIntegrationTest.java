package com.testragem.server;

import com.testragem.server.strategy.service.StrategyFactory;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.dataset.model.DatasetCoverage;
import com.testragem.server.backtest.model.TradeFill;
import com.testragem.server.backtest.model.BacktestOutcome;
import com.testragem.server.backtest.dto.BacktestResponse;
import com.testragem.server.backtest.dto.BacktestSummary;
import com.testragem.server.backtest.dto.BacktestHistory;

import com.testragem.server.backtest.validation.ConfigurationValidator;
import com.testragem.server.strategy.rules.*;
import com.testragem.server.dataset.service.DatasetService;
import com.testragem.server.backtest.repository.BacktestRepository;
import com.testragem.server.backtest.validation.DefaultConfigurationValidator;
import com.testragem.server.strategy.service.DefaultStrategyFactory;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL migrations + real HTTP/security; strategy/validator are TEST FIXTURES ONLY. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BacktestIntegrationTest.Fixtures.class)
@EnabledIfEnvironmentVariable(named="BACKTEST_TEST_DB_URL",matches=".+")
class BacktestIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json; @Autowired JdbcClient jdbc; @Autowired PasswordEncoder passwords;
    @Autowired DatasetService datasets; @Autowired BacktestRepository repository;
    @Autowired FixtureValidator fixtureValidator;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv("BACKTEST_TEST_DB_URL"));
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("BACKTEST_TEST_DB_USERNAME","testragem_test"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("BACKTEST_TEST_DB_PASSWORD",""));
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.baseline-on-migrate",()->"false");
        registry.add("backtest.dataset-csv",()->Path.of("src/test/resources/market-fixture.csv").toAbsolutePath().toString());
    }
    private final String password="Test password for backtests!";
    private Browser signedIn() throws Exception {
        String email=UUID.randomUUID()+"@example.com"; UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES (:id,:email,'Researcher',:hash,CURRENT_TIMESTAMP)")
            .param("id",id).param("email",email).param("hash",passwords.encode(password)).update();
        Browser browser=new Browser();assertThat(browser.post("/api/auth/login",Map.of("email",email,"password",password)).statusCode()).isEqualTo(200);return browser;
    }
    private BacktestRequest request() {var data=datasets.list().getFirst();return new BacktestRequest(data.id(),"buy-and-hold",data.coverageStart(),data.coverageEnd(),new BigDecimal("1000"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("100"),Map.of());}
    @Test void fullRunPersistsAndOwnershipSurvivesNewSession() throws Exception {
        Browser owner=signedIn(),other=signedIn();
        var response=owner.post("/api/backtests",request());assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        BacktestResponse saved=json.readValue(response.body(),BacktestResponse.class);
        assertThat(saved.result().finalEquity()).isEqualByComparingTo("1250");
        var chart = owner.get("/api/backtests/"+saved.id()+"/chart");
        assertThat(chart.statusCode()).as(chart.body()).isEqualTo(200);
        assertThat(json.readTree(chart.body()).get("candles").size()).isEqualTo(3);
        assertThat(json.readTree(chart.body()).get("dataset").get("fingerprint").asText()).isEqualTo(saved.datasetFingerprint());
        assertThat(other.get("/api/backtests/"+saved.id()+"/chart").statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/api/backtests/"+saved.id()+"/chart").statusCode()).isEqualTo(401);
        var restored = json.readValue(owner.get("/api/backtests/"+saved.id()).body(),BacktestResponse.class);
        assertThat(restored.result().indicatorPoints()).isNotEmpty();
        assertThat(restored.result().indicatorPoints()).isEqualTo(saved.result().indicatorPoints());

        assertThat(owner.get("/api/backtests/"+saved.id()).statusCode()).isEqualTo(200);
        assertThat(other.get("/api/backtests/"+saved.id()).statusCode()).isEqualTo(404);
        BacktestHistory history=json.readValue(owner.get("/api/backtests").body(),BacktestHistory.class);assertThat(history.items()).extracting(BacktestSummary::id).contains(saved.id());
        assertThat(json.readValue(other.get("/api/backtests").body(),BacktestHistory.class).items()).noneMatch(item->item.id().equals(saved.id()));
        assertThat(new Browser().get("/api/backtests").statusCode()).isEqualTo(401);
        assertThat(jdbc.sql("SELECT count(*) FROM trade_fills WHERE config_id=:id").param("id",saved.id()).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM equity_points WHERE config_id=:id").param("id",saved.id()).query(Integer.class).single()).isEqualTo(3);
    }
    @Test void failedDetailInsertRollsBackConfigurationAndResult() throws Exception {
        UUID owner=jdbc.sql("SELECT id FROM users LIMIT 1").query(UUID.class).optional().orElseGet(()->{UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO users(id,email,display_name) VALUES (:id,:email,'Fixture')").param("id",id).param("email",id+"@example.com").update();return id;});
        var input=request();BacktestConfiguration config=new BacktestConfiguration(input.datasetId(),input.strategy(),"1",input.rangeStart(),input.rangeEnd(),input.initialCapital(),input.feeBps(),input.slippageBps(),input.allocationPercent(),Map.of());
        long before=jdbc.sql("SELECT count(*) FROM backtest_configurations").query(Long.class).single();
        var bad=new BacktestOutcome(BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"test",List.of(new TradeFill(1,Instant.now(),"INVALID",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ONE)),List.of());
        assertThatThrownBy(()->repository.save(owner,config,"test",bad)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM backtest_configurations").query(Long.class).single()).isEqualTo(before);
    }
    @Test void paginationAndMissingCsrfAreRejected() throws Exception {
        Browser browser=signedIn();assertThat(browser.get("/api/backtests?page=-1").statusCode()).isEqualTo(400);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/backtests")).header("Cookie",browser.cookie).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request()))).build();
        assertThat(HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void pendingModulesFailClosedOverHttp() throws Exception {
        Browser browser=signedIn();fixtureValidator.enabled.set(false);
        try {
            assertThat(browser.post("/api/backtests",request()).statusCode()).isEqualTo(501);
            assertThat(browser.get("/api/strategies").body()).doesNotContain("\"available\":true");
        } finally {fixtureValidator.enabled.set(true);}
    }
    @Test void fractionalIntegerParametersAreNotSilentlyTruncated() throws Exception {
        Browser browser=signedIn();
        var response=browser.send("/api/backtests",json.writeValueAsString(request()).replace("\"parameters\":{}","\"parameters\":{\"fastPeriod\":2.5}"),json.readTree(browser.get("/api/auth/csrf").body()).get("token").asText());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test void candleEndpointRequiresValidBoundedRangesAndSession() throws Exception {
        Browser browser = signedIn();
        var dataset = datasets.list().getFirst();
        String path = "/api/datasets/"+dataset.id()+"/candles";
        assertThat(browser.get(path).statusCode()).isEqualTo(400);
        assertThat(browser.get(path+"?start=invalid&end=invalid").statusCode()).isEqualTo(400);
        assertThat(browser.get(path+"?start="+dataset.coverageEnd()+"&end="+dataset.coverageStart()).statusCode()).isEqualTo(400);
        var large = browser.get(path+"?start="+dataset.coverageStart()+"&end="+dataset.coverageStart().plusSeconds(10000L*3600));
        assertThat(large.statusCode()).isEqualTo(400);
        assertThat(large.body()).contains("chart_bar_limit");
        String valid = path+"?start="+dataset.coverageStart()+"&end="+dataset.coverageEnd();
        assertThat(browser.get(valid).statusCode()).isEqualTo(200);
        assertThat(new Browser().get(valid).statusCode()).isEqualTo(401);
    }

    @Test void chartRejectsDifferentDatasetFingerprintWithoutHidingSavedAccounting() throws Exception {
        Browser browser = signedIn();
        var response = browser.post("/api/backtests", request());
        assertThat(response.statusCode()).isEqualTo(201);
        var saved = json.readValue(response.body(), BacktestResponse.class);
        jdbc.sql("UPDATE backtest_results SET dataset_fingerprint=:hash WHERE config_id=:id")
            .param("hash", "f".repeat(64)).param("id", saved.id()).update();
        var chart = browser.get("/api/backtests/"+saved.id()+"/chart");
        assertThat(chart.statusCode()).isEqualTo(409);
        assertThat(chart.body()).contains("dataset_snapshot_unavailable");
        assertThat(browser.get("/api/backtests/"+saved.id()).statusCode()).isEqualTo(200);
    }

    static class FixtureValidator implements ConfigurationValidator {
        final AtomicBoolean enabled=new AtomicBoolean(true);
        public boolean ready(){return enabled.get();}
        public BacktestConfiguration validate(BacktestRequest r,DatasetCoverage c){return new BacktestConfiguration(r.datasetId(),r.strategy(),"fixture-1",r.rangeStart(),r.rangeEnd(),r.initialCapital(),r.feeBps(),r.slippageBps(),r.allocationPercent(),r.parameters());}
    }
    class Browser {
        String cookie="";final HttpClient client=HttpClient.newHttpClient();
        HttpResponse<String> get(String path)throws Exception{return send(path,null,null);}
        HttpResponse<String> post(String path,Object body)throws Exception{var csrf=json.readTree(get("/api/auth/csrf").body());return send(path,json.writeValueAsString(body),csrf.get("token").asText());}
        HttpResponse<String> send(String path,String body,String token)throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(!cookie.isEmpty())builder.header("Cookie",cookie);
            if(token!=null)builder.header("X-CSRF-TOKEN",token);
            if(body!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            response.headers().firstValue("set-cookie").ifPresent(value->cookie=value.split(";",2)[0]);return response;
        }
    }
    @TestConfiguration static class Fixtures {
        @Bean @Primary FixtureValidator fixtureValidator(){return new FixtureValidator();}
        @Bean @Primary StrategyFactory fixtureStrategies(){return new StrategyFactory(){public boolean ready(String key){return key.equals("buy-and-hold");}public TradingStrategy create(BacktestConfiguration c){return new TradingStrategy() {
            java.math.BigDecimal close;
            public Signal onStart() { return Signal.BUY; }
            public Signal onCandleClose(com.testragem.server.dataset.model.Candle candle) { close=candle.close(); return Signal.HOLD; }
            public Map<String,java.math.BigDecimal> indicatorValues() { return Map.of("fastSma",close); }
        };}};}
    }
}
