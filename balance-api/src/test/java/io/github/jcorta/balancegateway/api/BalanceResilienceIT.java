package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Resilience behavior over real HTTP, with the settings of application.yml except for shorter waits. */
class BalanceResilienceIT extends CoreBackedApiSupport {

    private static final String AS_OF = "2026-11-26T14:03:12.481Z";

    @Test
    void returnsBalanceReportedByTheCore() {
        core.stubFor(balanceRequest("ACC-1001").willReturn(balanceBody("ACC-1001", "152300.75", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-1001");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonString(response, "$.accountId")).isEqualTo("ACC-1001");
        assertThat(jsonString(response, "$.currency")).isEqualTo("ARS");
        assertThat(jsonString(response, "$.asOf")).isEqualTo(AS_OF);
        assertThat(response.getBody()).contains("\"balance\":152300.75").contains("\"stale\":false");
    }

    @Test
    void rejectsMalformedAccountIdWithoutCallingTheCore() {
        ResponseEntity<String> response = requestBalance("ACC_1001!");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(jsonString(response, "$.type")).isEqualTo("urn:balance-gateway:problem:invalid-request");
        core.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void retriesServerErrorsAndReturnsTheBalanceOnceTheCoreRecovers() {
        core.stubFor(balanceRequest("ACC-RETRY-5XX").inScenario("5xx")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        core.stubFor(balanceRequest("ACC-RETRY-5XX").inScenario("5xx")
                .whenScenarioStateIs("recovered")
                .willReturn(balanceBody("ACC-RETRY-5XX", "10.00", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-RETRY-5XX");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"stale\":false");
        core.verify(2, balanceRequested("ACC-RETRY-5XX"));
    }

    @Test
    void retriesReadTimeoutsAndReturnsTheBalanceOnceTheCoreRecovers() {
        core.stubFor(balanceRequest("ACC-RETRY-TIMEOUT").inScenario("timeout")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(balanceBody("ACC-RETRY-TIMEOUT", "10.00", AS_OF).withFixedDelay(1_000))
                .willSetStateTo("recovered"));
        core.stubFor(balanceRequest("ACC-RETRY-TIMEOUT").inScenario("timeout")
                .whenScenarioStateIs("recovered")
                .willReturn(balanceBody("ACC-RETRY-TIMEOUT", "10.00", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-RETRY-TIMEOUT");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        core.verify(2, balanceRequested("ACC-RETRY-TIMEOUT"));
    }

    @Test
    void doesNotRetryWhenTheCoreRejectsTheRequest() {
        core.stubFor(balanceRequest("ACC-REJECTED").willReturn(aResponse().withStatus(400)));

        ResponseEntity<String> response = requestBalance("ACC-REJECTED");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(jsonString(response, "$.type")).isEqualTo("urn:balance-gateway:problem:core-rejected-request");
        core.verify(1, balanceRequested("ACC-REJECTED"));
    }

    @Test
    void returnsNotFoundWithoutRepeatingTheCallWhenCoreDoesNotKnowTheAccount() {
        core.stubFor(balanceRequest("ACC-9999").willReturn(aResponse().withStatus(404)));

        ResponseEntity<String> response = requestBalance("ACC-9999");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(jsonString(response, "$.type")).isEqualTo("urn:balance-gateway:problem:account-not-found");
        core.verify(1, balanceRequested("ACC-9999"));
    }

    @Test
    void returnsServiceUnavailableWhenTheCoreFailsAndNoBalanceIsKnown() {
        core.stubFor(balanceRequest("ACC-NO-CACHE").willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = requestBalance("ACC-NO-CACHE");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(jsonString(response, "$.type")).isEqualTo("urn:balance-gateway:problem:balance-unavailable");
        assertThat(response.getBody()).doesNotContain("Exception");
        core.verify(3, balanceRequested("ACC-NO-CACHE"));
    }

    @Test
    void returnsTheLastKnownBalanceMarkedStaleWithItsOriginalAsOfWhenTheCoreFails() {
        core.stubFor(balanceRequest("ACC-STALE").willReturn(balanceBody("ACC-STALE", "99.50", AS_OF)));
        ResponseEntity<String> current = requestBalance("ACC-STALE");
        assertThat(current.getBody()).contains("\"stale\":false");

        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-STALE").willReturn(aResponse().withStatus(500)));
        ResponseEntity<String> stale = requestBalance("ACC-STALE");

        assertThat(stale.getStatusCode().value()).isEqualTo(200);
        assertThat(stale.getBody()).contains("\"stale\":true").contains("\"balance\":99.50");
        assertThat(jsonString(stale, "$.asOf")).isEqualTo(AS_OF);
    }

    @Test
    void opensTheCircuitAfterSustainedServerErrorsAndStopsCallingTheCore() {
        core.stubFor(balanceRequest("ACC-OPEN").willReturn(aResponse().withStatus(500)));

        requestBalance("ACC-OPEN");
        requestBalance("ACC-OPEN");

        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        core.verify(5, balanceRequested("ACC-OPEN"));

        ResponseEntity<String> whileOpen = requestBalance("ACC-OPEN");

        assertThat(whileOpen.getStatusCode().value()).isEqualTo(503);
        core.verify(5, balanceRequested("ACC-OPEN"));
    }

    @Test
    void servesStaleBalanceWithoutCallingTheCoreWhileTheCircuitIsOpen() {
        core.stubFor(balanceRequest("ACC-OPEN-STALE").willReturn(balanceBody("ACC-OPEN-STALE", "7.00", AS_OF)));
        requestBalance("ACC-OPEN-STALE");
        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-OPEN-STALE").willReturn(aResponse().withStatus(500)));
        requestBalance("ACC-OPEN-STALE");
        requestBalance("ACC-OPEN-STALE");
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = core.findAll(balanceRequested("ACC-OPEN-STALE")).size();

        ResponseEntity<String> response = requestBalance("ACC-OPEN-STALE");

        assertThat(response.getBody()).contains("\"stale\":true");
        assertThat(core.findAll(balanceRequested("ACC-OPEN-STALE"))).hasSize(callsBefore);
    }

    @Test
    void answersFromTheLastKnownBalanceQuicklyWhileTheCircuitIsOpen() {
        core.stubFor(balanceRequest("ACC-FAST").willReturn(balanceBody("ACC-FAST", "3.00", AS_OF)));
        requestBalance("ACC-FAST");
        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-FAST").willReturn(aResponse().withStatus(500)));
        requestBalance("ACC-FAST");
        requestBalance("ACC-FAST");
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        ResponseEntity<String> response = requestBalance("ACC-FAST");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getBody()).contains("\"stale\":true");
        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
    }

    @Test
    void opensTheCircuitAgainWhenTheTrialRequestsFailAndLimitsThemToTwo() {
        core.stubFor(balanceRequest("ACC-TRIAL").willReturn(aResponse().withStatus(500)));
        requestBalance("ACC-TRIAL");
        requestBalance("ACC-TRIAL");
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> coreCircuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);
        int callsBeforeTrial = core.findAll(balanceRequested("ACC-TRIAL")).size();

        ResponseEntity<String> response = requestBalance("ACC-TRIAL");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(core.findAll(balanceRequested("ACC-TRIAL"))).hasSize(callsBeforeTrial + 2);
    }

    @Test
    void closesTheCircuitAgainOnceTheCoreRecovers() {
        core.stubFor(balanceRequest("ACC-RECOVER").willReturn(aResponse().withStatus(500)));
        requestBalance("ACC-RECOVER");
        requestBalance("ACC-RECOVER");
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-RECOVER").willReturn(balanceBody("ACC-RECOVER", "5.00", AS_OF)));
        await().atMost(Duration.ofSeconds(10))
                .until(() -> coreCircuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

        ResponseEntity<String> first = requestBalance("ACC-RECOVER");
        ResponseEntity<String> second = requestBalance("ACC-RECOVER");

        assertThat(first.getBody()).contains("\"stale\":false");
        assertThat(second.getBody()).contains("\"stale\":false");
        assertThat(coreCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
