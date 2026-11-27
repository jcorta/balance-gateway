package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Real signed tokens validated against a JWKS served over HTTP, as in a deployment. */
class JwtValidationIT extends CoreBackedApiSupport {

    private static final String UNAUTHENTICATED = "urn:balance-gateway:problem:unauthenticated";
    private static final String ACCOUNT = "ACC-SEC";

    @Test
    void acceptsATokenWithTheBalanceReadScope() {
        core.stubFor(balanceRequest(ACCOUNT)
                .willReturn(balanceBody(ACCOUNT, "1.00", "2026-11-26T14:03:12Z")));

        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.valid("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void rejectsARequestWithoutToken() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(jsonString(response, "$.type")).isEqualTo(UNAUTHENTICATED);
        assertThat(jsonString(response, "$.instance")).isEqualTo("/v1/accounts/" + ACCOUNT + "/balance");
    }

    @Test
    void rejectsAnExpiredToken() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.expired("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(jsonString(response, "$.type")).isEqualTo(UNAUTHENTICATED);
    }

    @Test
    void rejectsATokenWhosePayloadWasEditedAfterSigning() {
        String forged = TestTokens.withScopeEditedAfterSigning("reporting.read", "balance.read");

        ResponseEntity<String> response = requestBalance(ACCOUNT, forged);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        core.verify(0, anyRequestedFor(urlPathMatching("/core/.*")));
    }

    @Test
    void rejectsAnUnsignedToken() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.unsigned("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        core.verify(0, anyRequestedFor(urlPathMatching("/core/.*")));
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.fromAnotherIssuer("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsATokenSignedWithAKeyThatTheIssuerDoesNotPublish() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.signedWithAnUnknownKey("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsATokenThatIsNotAJwt() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, "not-a-jwt");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void forbidsAValidTokenWithoutTheBalanceReadScope() {
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.validForClient("reporting", "reporting.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(jsonString(response, "$.type")).isEqualTo("urn:balance-gateway:problem:forbidden");
        assertThat(jsonString(response, "$.instance")).isEqualTo("/v1/accounts/" + ACCOUNT + "/balance");
    }

    @Test
    void doesNotCallTheCoreForUnauthorizedRequests() {
        requestBalance(ACCOUNT, null);
        requestBalance(ACCOUNT, TestTokens.expired("balance.read"));
        requestBalance(ACCOUNT, TestTokens.validForClient("reporting", "reporting.read"));

        core.verify(0, anyRequestedFor(urlPathMatching("/core/.*")));
    }

    @Test
    void validatesTokensEvenIfTheJwksBecomesUnavailableAfterItWasFetched() {
        core.stubFor(balanceRequest(ACCOUNT)
                .willReturn(balanceBody(ACCOUNT, "1.00", "2026-11-26T14:03:12Z")));
        requestBalance(ACCOUNT);

        core.resetAll();
        core.stubFor(balanceRequest(ACCOUNT).willReturn(aResponse().withStatus(404)));
        ResponseEntity<String> response = requestBalance(ACCOUNT, TestTokens.valid("balance.read"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
