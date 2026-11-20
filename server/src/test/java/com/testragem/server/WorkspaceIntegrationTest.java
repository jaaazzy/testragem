package com.testragem.server;

import com.testragem.server.backtest.engine.ExecutionEngine;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.backtest.repository.BacktestRepository;
import com.testragem.server.dataset.service.DatasetService;
import com.testragem.server.strategy.rules.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.net.CookieManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL migrations, session/CSRF, ownership, and durable personal research. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "BACKTEST_TEST_DB_URL", matches = ".+")
class WorkspaceIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired DatasetService datasets;
    @Autowired BacktestRepository runs;
    UUID owner, other;
    Browser browser, outsider;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BACKTEST_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("BACKTEST_TEST_DB_USERNAME", "testragem_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("BACKTEST_TEST_DB_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.target", () -> "latest");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
        registry.add("backtest.dataset-csv", () -> java.nio.file.Path.of("src/test/resources/market-fixture.csv").toAbsolutePath().toString());
    }

    @BeforeEach void accounts() throws Exception {
        owner = UUID.randomUUID(); other = UUID.randomUUID();
        browser = account(owner); outsider = account(other);
    }

    private Browser account(UUID id) throws Exception {
        String email = id + "@example.com";
        jdbc.sql("INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES (:id,:email,'Workspace test',:hash,CURRENT_TIMESTAMP)")
                .param("id", id).param("email", email).param("hash", passwords.encode("Workspace test password!")).update();
        var session = new Browser();
        assertThat(session.send("POST", "/api/auth/login", Map.of("email", email, "password", "Workspace test password!"), true).statusCode()).isEqualTo(200);
        return session;
    }

    private Map<String, Object> configuration() {
        var dataset = datasets.list().getFirst();
        return Map.of("datasetId", dataset.id(), "strategy", "buy-and-hold", "rangeStart", dataset.coverageStart(),
                "rangeEnd", dataset.coverageEnd(), "initialCapital", 10000, "feeBps", 10,
                "slippageBps", 5, "allocationPercent", 100, "parameters", Map.of());
    }

    private UUID completedRun(UUID user) {
        var dataset = datasets.list().getFirst();
        var config = new BacktestConfiguration(dataset.id(), "buy-and-hold", "1", dataset.coverageStart(), dataset.coverageEnd(),
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), Map.of());
        var strategy = new TradingStrategy() {
            public Signal onStart() { return Signal.BUY; }
            public Signal onCandleClose(com.testragem.server.dataset.model.Candle candle) { return Signal.HOLD; }
        };
        var result = new ExecutionEngine().run(config, datasets.candles(dataset.id(), config.rangeStart(), config.rangeEnd()), strategy);
        return runs.save(user, config, dataset.fingerprint(), result).id();
    }

    @Test void privateRoutesRequireAuthenticationAndMutationsRequireCsrf() throws Exception {
        var guest = new Browser();
        for (String path : List.of("/api/workspace/preferences", "/api/workspace/presets", "/api/workspace/annotations?ids=" + UUID.randomUUID(), "/api/backtests/compare?ids=" + UUID.randomUUID() + "," + UUID.randomUUID())) {
            assertThat(guest.send("GET", path, null, false).statusCode()).isEqualTo(401);
        }
        assertThat(browser.send("PUT", "/api/workspace/preferences", Map.of("evaluationGoal", "return"), false).statusCode()).isEqualTo(403);
    }

    @Test void presetsPreserveVersionsAndHideOtherOwners() throws Exception {
        var created = browser.send("POST", "/api/workspace/presets", Map.of("name", "  BTC baseline  ", "configuration", configuration()), true);
        assertThat(created.statusCode()).isEqualTo(201);
        var preset = json.readTree(created.body());
        String id = preset.get("id").asString();
        assertThat(preset.get("name").asString()).isEqualTo("BTC baseline");
        var changed = new HashMap<>(configuration()); changed.put("initialCapital", 20000);
        var revised = browser.send("POST", "/api/workspace/presets/" + id + "/versions", Map.of("name", "Revised", "configuration", changed), true);
        assertThat(revised.statusCode()).isEqualTo(201);
        assertThat(json.readTree(revised.body()).get("version").asInt()).isEqualTo(2);
        var history = json.readTree(browser.send("GET", "/api/workspace/presets/" + id + "/versions", null, false).body());
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(1).get("configuration").get("initialCapital").decimalValue()).isEqualByComparingTo("10000");
        var latest = json.readTree(browser.send("GET", "/api/workspace/presets", null, false).body());
        assertThat(latest.get(0).get("version").asInt()).isEqualTo(2);
        assertThat(json.readTree(outsider.send("GET", "/api/workspace/presets", null, false).body()).size()).isZero();
        assertThat(outsider.send("GET", "/api/workspace/presets/" + id + "/versions", null, false).statusCode()).isEqualTo(404);
        assertThat(outsider.send("POST", "/api/workspace/presets/" + id + "/versions", Map.of("name", "Foreign", "configuration", configuration()), true).statusCode()).isEqualTo(404);
    }

    @Test void preferencesPersistPerOwnerAndUseFinancialValidation() throws Exception {
        var settings = Map.of("initialCapital", 5000, "feeBps", 10, "slippageBps", 5, "allocationPercent", 25, "evaluationGoal", "drawdown");
        assertThat(browser.send("PUT", "/api/workspace/preferences", settings, true).statusCode()).isEqualTo(200);
        var saved = json.readTree(browser.send("GET", "/api/workspace/preferences", null, false).body());
        assertThat(saved.get("initialCapital").asInt()).isEqualTo(5000);
        assertThat(saved.get("evaluationGoal").asString()).isEqualTo("drawdown");
        assertThat(json.readTree(outsider.send("GET", "/api/workspace/preferences", null, false).body()).get("initialCapital").asInt()).isEqualTo(10000);
        var invalid = new HashMap<String, Object>(settings); invalid.put("feeBps", -1);
        var rejection = browser.send("PUT", "/api/workspace/preferences", invalid, true);
        assertThat(rejection.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejection.body()).get("fields").has("feeBps")).isTrue();
        invalid.put("feeBps", 10); invalid.put("evaluationGoal", "profit-guarantee");
        assertThat(browser.send("PUT", "/api/workspace/preferences", invalid, true).statusCode()).isEqualTo(400);
    }

    @Test void annotationsAreOwnerScopedAndDoNotChangeSimulationResults() throws Exception {
        UUID id = completedRun(owner);
        var before = runs.get(owner, id);
        var annotation = Map.of("title", "  January study  ", "notes", "Investigate costs next.", "tags", List.of("baseline", "baseline", "btc"));
        var saved = browser.send("PUT", "/api/workspace/annotations/" + id, annotation, true);
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(json.readTree(saved.body()).get("tags").size()).isEqualTo(2);
        var read = json.readTree(browser.send("GET", "/api/workspace/annotations?ids=" + id, null, false).body());
        assertThat(read.get(0).get("annotation").get("title").asString()).isEqualTo("January study");
        assertThat(json.readTree(outsider.send("GET", "/api/workspace/annotations?ids=" + id, null, false).body()).size()).isZero();
        assertThat(outsider.send("PUT", "/api/workspace/annotations/" + id, annotation, true).statusCode()).isEqualTo(404);
        assertThat(browser.send("PUT", "/api/workspace/annotations/" + UUID.randomUUID(), annotation, true).statusCode()).isEqualTo(404);
        assertThat(runs.get(owner, id)).isEqualTo(before);
    }

    @Test void comparisonRejectsForeignAndInvalidSelections() throws Exception {
        UUID first = completedRun(owner), second = completedRun(owner), foreign = completedRun(other);
        var response = browser.send("GET", "/api/backtests/compare?ids=" + first + "," + second, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).size()).isEqualTo(2);
        assertThat(browser.send("GET", "/api/backtests/compare?ids=" + first + "," + foreign, null, false).statusCode()).isEqualTo(404);
        assertThat(browser.send("GET", "/api/backtests/compare?ids=" + first + "," + first, null, false).statusCode()).isEqualTo(400);
        assertThat(browser.send("GET", "/api/backtests/compare?ids=invalid", null, false).statusCode()).isEqualTo(400);
    }

    @Test void rejectsOversizedAndMissingPersonalFields() throws Exception {
        assertThat(browser.send("POST", "/api/workspace/presets", Map.of("name", " ", "configuration", configuration()), true).statusCode()).isEqualTo(400);
        assertThat(browser.send("POST", "/api/workspace/presets", Map.of("name", "Missing config"), true).statusCode()).isEqualTo(400);
        var annotation = Map.of("title", "Test", "notes", "x".repeat(4001), "tags", List.of());
        assertThat(browser.send("PUT", "/api/workspace/annotations/" + completedRun(owner), annotation, true).statusCode()).isEqualTo(400);
    }

    class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method, String path, Object body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (csrf) {
                var token = json.readTree(send("GET", "/api/auth/csrf", null, false).body());
                request.header(token.get("headerName").asString(), token.get("token").asString());
            }
            request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
