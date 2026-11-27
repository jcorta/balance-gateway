package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ObservabilityIT extends CoreBackedApiSupport {

    private static final String BALANCE_OPERATION = "$.paths['/v1/accounts/{accountId}/balance'].get";

    @LocalManagementPort
    private int managementPort;

    private RestClient management;

    @BeforeEach
    void createManagementClient() {
        management = RestClient.builder()
                .baseUrl("http://localhost:" + managementPort)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    @Test
    void reportsLivenessAsUpWhileTheCircuitToTheCoreIsOpen() {
        openTheCircuit("ACC-OBS-LIVE");

        ResponseEntity<String> liveness = actuator("/actuator/health/liveness");
        ResponseEntity<String> health = actuator("/actuator/health");

        assertThat(liveness.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonString(liveness, "$.status")).isEqualTo("UP");
        assertThat(health.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonString(health, "$.status")).isEqualTo("UP");
    }

    @Test
    void reportsTheStateOfTheCircuitBreakerInTheHealthEndpoint() {
        assertThat(jsonString(actuator("/actuator/health"), "$.components.circuitBreakers.details.core.details.state"))
                .isEqualTo("CLOSED");
        assertThat(metricValue("resilience4j.circuitbreaker.state?tag=state:closed")).isEqualTo(1.0);

        openTheCircuit("ACC-OBS-STATE");

        ResponseEntity<String> health = actuator("/actuator/health");
        assertThat(jsonString(health, "$.components.circuitBreakers.details.core.details.state")).isEqualTo("OPEN");
        assertThat(jsonString(health, "$.components.circuitBreakers.details.core.status")).isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void exposesTheCircuitBreakerAndRetryEndpoints() {
        openTheCircuit("ACC-OBS-ENDPOINTS");

        assertThat(jsonString(actuator("/actuator/circuitbreakers"), "$.circuitBreakers.core.state")).isEqualTo("OPEN");
        assertThat(JsonPath.<List<String>>read(actuator("/actuator/retries").getBody(), "$.retries"))
                .contains("core");
        assertThat(actuator("/actuator/circuitbreakerevents").getStatusCode().value()).isEqualTo(200);
        assertThat(actuator("/actuator/retryevents").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void exposesResilienceMetricsForRetriesAndTheCircuitBreaker() {
        openTheCircuit("ACC-OBS-METRICS");

        List<String> names = JsonPath.read(actuator("/actuator/metrics").getBody(), "$.names");

        assertThat(names).contains("resilience4j.circuitbreaker.state", "resilience4j.circuitbreaker.calls",
                "resilience4j.retry.calls", "http.server.requests");
        ResponseEntity<String> state = actuator("/actuator/metrics/resilience4j.circuitbreaker.state?tag=state:open");
        assertThat(JsonPath.<Double>read(state.getBody(), "$.measurements[0].value")).isEqualTo(1.0);
        assertThat(metricValue("resilience4j.retry.calls?tag=kind:failed_with_retry")).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void reportsTheTrialStateOfTheCircuitBreakerWhileItProbesTheCore() {
        openTheCircuit("ACC-OBS-TRIAL");
        coreCircuitBreaker.transitionToHalfOpenState();

        ResponseEntity<String> health = actuator("/actuator/health");

        assertThat(jsonString(health, "$.components.circuitBreakers.details.core.details.state")).isEqualTo("HALF_OPEN");
        assertThat(metricValue("resilience4j.circuitbreaker.state?tag=state:half_open")).isEqualTo(1.0);
        assertThat(jsonString(actuator("/actuator/health/liveness"), "$.status")).isEqualTo("UP");
    }

    @Test
    void countsDegradedResponsesByOutcome() {
        double staleBefore = fallbackCount("stale");
        double unavailableBefore = fallbackCount("unavailable");
        core.stubFor(balanceRequest("ACC-OBS-STALE").willReturn(balanceBody("ACC-OBS-STALE", "4.00", "2026-11-26T14:03:12Z")));
        requestBalance("ACC-OBS-STALE");
        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-OBS-STALE").willReturn(aResponse().withStatus(500)));
        core.stubFor(balanceRequest("ACC-OBS-NONE").willReturn(aResponse().withStatus(500)));

        requestBalance("ACC-OBS-STALE");
        requestBalance("ACC-OBS-NONE");

        assertThat(fallbackCount("stale")).isEqualTo(staleBefore + 1);
        assertThat(fallbackCount("unavailable")).isEqualTo(unavailableBefore + 1);
    }

    @Test
    void describesTheBalanceOperationWithItsSecurityRequirementAndEveryErrorResponse() {
        ResponseEntity<String> document = getFromApi("/v3/api-docs");

        assertThat(document.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> responses = JsonPath.read(document.getBody(), BALANCE_OPERATION + ".responses");
        assertThat(responses).containsOnlyKeys("200", "400", "401", "403", "404", "500", "502", "503");
        for (String status : List.of("400", "401", "403", "404", "500", "502", "503")) {
            String schema = BALANCE_OPERATION + ".responses['" + status + "'].content['application/problem+json'].schema['$ref']";
            assertThat(JsonPath.<String>read(document.getBody(), schema)).endsWith("/ApiProblem");
        }
        assertThat(JsonPath.<List<String>>read(document.getBody(), BALANCE_OPERATION + ".security[0].clientCredentials"))
                .containsExactly("balance.read");
    }

    @Test
    void describesTheClientCredentialsSecuritySchemeAndTheErrorBody() {
        ResponseEntity<String> document = getFromApi("/v3/api-docs");

        assertThat(jsonString(document, "$.components.securitySchemes.clientCredentials.type")).isEqualTo("oauth2");
        assertThat(jsonString(document, "$.components.securitySchemes.clientCredentials.flows.clientCredentials.tokenUrl"))
                .isEqualTo("http://localhost:9000/oauth2/token");
        assertThat(jsonString(document, "$.components.securitySchemes.clientCredentials.flows.clientCredentials.scopes['balance.read']"))
                .isNotBlank();
        Map<String, Object> problemProperties =
                JsonPath.read(document.getBody(), "$.components.schemas.ApiProblem.properties");
        assertThat(problemProperties).containsKeys("type", "title", "status", "detail", "instance", "correlationId");
        Map<String, Object> balanceProperties =
                JsonPath.read(document.getBody(), "$.components.schemas.BalanceResponse.properties");
        assertThat(balanceProperties).containsKeys("accountId", "balance", "currency", "asOf", "stale");
    }

    @Test
    void servesTheDocumentationWithoutCredentials() {
        ResponseEntity<String> swagger = getFromApi("/swagger-ui/index.html");

        assertThat(swagger.getStatusCode().value()).isEqualTo(200);
    }

    private void openTheCircuit(String accountId) {
        core.stubFor(balanceRequest(accountId).willReturn(aResponse().withStatus(500)));
        requestBalance(accountId);
        requestBalance(accountId);
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private ResponseEntity<String> actuator(String path) {
        return management.get().uri(path).retrieve().toEntity(String.class);
    }

    private double metricValue(String pathAndQuery) {
        return JsonPath.<Double>read(actuator("/actuator/metrics/" + pathAndQuery).getBody(), "$.measurements[0].value");
    }

    private double fallbackCount(String outcome) {
        ResponseEntity<String> metric = actuator("/actuator/metrics/balance.fallback?tag=outcome:" + outcome);
        if (metric.getStatusCode().value() == 404) {
            return 0;
        }
        return JsonPath.<Double>read(metric.getBody(), "$.measurements[0].value");
    }
}
