package io.github.jcorta.balancegateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "IVR_CLIENT_SECRET=ivr-test-secret",
        "REPORTING_CLIENT_SECRET=reporting-test-secret"
})
class AuthServerIT {

    @LocalServerPort
    private int port;

    private RestClient authServer;

    @BeforeEach
    void createClient() {
        authServer = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    @Test
    void issuesAccessTokenWithBalanceReadScopeToTheIvrChannel() {
        ResponseEntity<String> response = requestToken("ivr-channel", "ivr-test-secret", "client_credentials", "balance.read");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.getBody(), "$.token_type")).isEqualToIgnoringCase("Bearer");
        Jwt jwt = decode(JsonPath.read(response.getBody(), "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo("ivr-channel");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("balance.read");
        assertThat(jwt.getIssuer()).hasToString("http://localhost:9000");
    }

    @Test
    void signsAccessTokensWithRs256AndPublishesTheKeyInTheJwks() {
        ResponseEntity<String> token = requestToken("ivr-channel", "ivr-test-secret", "client_credentials", "balance.read");
        String accessToken = JsonPath.read(token.getBody(), "$.access_token");

        ResponseEntity<String> jwks = authServer.get().uri("/oauth2/jwks").retrieve().toEntity(String.class);

        assertThat(jwks.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(jwks.getBody(), "$.keys[*].kty")).containsOnly("RSA");
        assertThat(decode(accessToken).getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    void issuesShortLivedAccessTokens() {
        ResponseEntity<String> response = requestToken("ivr-channel", "ivr-test-secret", "client_credentials", "balance.read");

        Jwt jwt = decode(JsonPath.read(response.getBody(), "$.access_token"));

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void refusesToGrantBalanceReadScopeToTheReportingClient() {
        ResponseEntity<String> response = requestToken("reporting", "reporting-test-secret", "client_credentials", "balance.read");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.getBody(), "$.error")).isEqualTo("invalid_scope");
    }

    @Test
    void grantsTheReportingClientOnlyItsOwnScope() {
        ResponseEntity<String> response = requestToken("reporting", "reporting-test-secret", "client_credentials", "reporting.read");

        Jwt jwt = decode(JsonPath.read(response.getBody(), "$.access_token"));

        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("reporting.read");
    }

    @Test
    void grantsNoScopeWhenNoneIsRequested() {
        ResponseEntity<String> response = requestToken("ivr-channel", "ivr-test-secret", "client_credentials", null);

        Jwt jwt = decode(JsonPath.read(response.getBody(), "$.access_token"));

        assertThat(jwt.getClaimAsStringList("scope")).isNull();
    }

    @Test
    void rejectsAWrongClientSecret() {
        ResponseEntity<String> response = requestToken("ivr-channel", "not-the-secret", "client_credentials", "balance.read");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.getBody(), "$.error")).isEqualTo("invalid_client");
    }

    @Test
    void rejectsGrantTypesOtherThanClientCredentials() {
        ResponseEntity<String> response = requestToken("ivr-channel", "ivr-test-secret", "password", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.getBody(), "$.error")).isEqualTo("unsupported_grant_type");
    }

    private ResponseEntity<String> requestToken(String clientId, String secret, String grantType, String scope) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        if (scope != null) {
            form.add("scope", scope);
        }
        return authServer.post().uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(String.class);
    }

    private Jwt decode(String accessToken) {
        return NimbusJwtDecoder.withJwkSetUri("http://localhost:" + port + "/oauth2/jwks").build().decode(accessToken);
    }
}
