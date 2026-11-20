package com.testragem.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Docker readiness must retain required storage checks without depending on optional integrations. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:dockerreadiness;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:pre-auth-schema.sql'"
})
@ActiveProfiles("test")
@Import(DockerReadinessIntegrationTest.OptionalFailure.class)
class DockerReadinessIntegrationTest {
    @LocalServerPort int port;
    @Autowired ConfigurableApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach void readyAgain() {
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test void optionalFailureRemainsVisibleButDoesNotBlockReadiness() throws Exception {
        var aggregate = get("/actuator/health");
        assertThat(aggregate.statusCode()).isEqualTo(503);
        assertThat(aggregate.body()).contains("DOWN").doesNotContain("private diagnostic");

        var readiness = get("/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("UP").doesNotContain("private diagnostic", "components");
        assertThat(context.getEnvironment().getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db,diskSpace");
    }

    @Test void refusesTrafficWhenTheApplicationIsNotReady() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(503);
    }

    @Test void readinessDoesNotExposeOtherActuatorRoutes() throws Exception {
        assertThat(get("/actuator/info").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/health/readiness/db").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class OptionalFailure {
        @Bean HealthIndicator unavailableExternalHealthIndicator() {
            return () -> Health.down().withDetail("error", "private diagnostic").build();
        }
    }
}
