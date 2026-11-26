package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdIT extends CoreBackedApiSupport {

    private static final String HEADER = "X-Correlation-Id";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String AS_OF = "2026-11-26T14:03:12Z";

    @Test
    void returnsTheCallersIdAndForwardsItToTheCore() {
        core.stubFor(balanceRequest("ACC-CID-1").willReturn(balanceBody("ACC-CID-1", "1.00", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-CID-1", TestTokens.valid("balance.read"), "caller-id-42");

        assertThat(response.getHeaders().getFirst(HEADER)).isEqualTo("caller-id-42");
        core.verify(1, balanceRequested("ACC-CID-1").withHeader(HEADER, equalTo("caller-id-42")));
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNoneAndForwardsIt() {
        core.stubFor(balanceRequest("ACC-CID-2").willReturn(balanceBody("ACC-CID-2", "1.00", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-CID-2");

        String generated = response.getHeaders().getFirst(HEADER);
        assertThat(generated).matches(UUID_PATTERN);
        core.verify(1, balanceRequested("ACC-CID-2").withHeader(HEADER, equalTo(generated)));
    }

    @Test
    void replacesAMalformedIdInsteadOfForwardingIt() {
        core.stubFor(balanceRequest("ACC-CID-3").willReturn(balanceBody("ACC-CID-3", "1.00", AS_OF)));

        ResponseEntity<String> response = requestBalance("ACC-CID-3", TestTokens.valid("balance.read"), "bad id;<script>");

        String replacement = response.getHeaders().getFirst(HEADER);
        assertThat(replacement).matches(UUID_PATTERN);
        core.verify(1, balanceRequested("ACC-CID-3").withHeader(HEADER, equalTo(replacement)));
        core.verify(0, balanceRequested("ACC-CID-3").withHeader(HEADER, equalTo("bad id;<script>")));
    }

    @Test
    void includesTheIdInEveryKindOfErrorBody() {
        core.stubFor(balanceRequest("ACC-CID-404").willReturn(aResponse().withStatus(404)));
        core.stubFor(balanceRequest("ACC-CID-503").willReturn(aResponse().withStatus(500)));
        String token = TestTokens.valid("balance.read");

        assertErrorCarriesId(requestBalance("ACC-CID-401", null, "cid-401"), 401, "cid-401");
        assertErrorCarriesId(requestBalance("ACC-CID-403", TestTokens.valid("reporting.read"), "cid-403"), 403, "cid-403");
        assertErrorCarriesId(requestBalance("ACC_BAD!", token, "cid-400"), 400, "cid-400");
        assertErrorCarriesId(requestBalance("ACC-CID-404", token, "cid-404"), 404, "cid-404");
        assertErrorCarriesId(requestBalance("ACC-CID-503", token, "cid-503"), 503, "cid-503");
    }

    @Test
    void appearsInTheLogEntriesOfTheRetriesAndOfTheFallback(CapturedOutput output) {
        core.stubFor(balanceRequest("ACC-CID-LOG").willReturn(aResponse().withStatus(500)));

        requestBalance("ACC-CID-LOG", TestTokens.valid("balance.read"), "log-trace-77");

        assertThat(output.getAll().lines())
                .anyMatch(line -> line.contains("Core call failed (attempt 1)") && line.contains("[log-trace-77]"))
                .anyMatch(line -> line.contains("no recent balance known") && line.contains("[log-trace-77]"));
    }

    @Test
    void appearsInTheLogEntryOfASuccessfulRequest(CapturedOutput output) {
        core.stubFor(balanceRequest("ACC-CID-OK").willReturn(balanceBody("ACC-CID-OK", "1.00", AS_OF)));

        requestBalance("ACC-CID-OK", TestTokens.valid("balance.read"), "happy-path-9");

        assertThat(output.getAll().lines())
                .anyMatch(line -> line.contains("Balance requested for account ACC-CID-OK") && line.contains("[happy-path-9]"));
    }

    @Test
    void usesTheGeneratedIdInTheErrorBodyAndInTheLogs(CapturedOutput output) {
        core.stubFor(balanceRequest("ACC-CID-GEN").willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = requestBalance("ACC-CID-GEN");

        String generated = response.getHeaders().getFirst(HEADER);
        assertThat(generated).matches(UUID_PATTERN);
        assertThat(jsonString(response, "$.correlationId")).isEqualTo(generated);
        assertThat(output.getAll().lines())
                .anyMatch(line -> line.contains("Core call failed (attempt 1)") && line.contains("[" + generated + "]"));
    }

    private void assertErrorCarriesId(ResponseEntity<String> response, int status, String correlationId) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst(HEADER)).isEqualTo(correlationId);
        assertThat(jsonString(response, "$.correlationId")).isEqualTo(correlationId);
    }
}
