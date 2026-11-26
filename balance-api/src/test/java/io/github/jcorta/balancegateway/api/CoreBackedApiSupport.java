package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Starts the application on a random port. One WireMock server plays both the core and the authorization server's
 * JWKS endpoint. It is shared by all integration test classes so that Spring contexts with the same configuration
 * are reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
abstract class CoreBackedApiSupport {

    private static final String JWKS_PATH = "/oauth2/jwks";

    static final WireMockServer core = startWireMock();

    @DynamicPropertySource
    static void externalSystems(DynamicPropertyRegistry registry) {
        registry.add("balance.core.base-url", core::baseUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestTokens.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> core.baseUrl() + JWKS_PATH);
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected CircuitBreaker coreCircuitBreaker;

    private RestClient api;

    @BeforeEach
    void createClientAndResetState() {
        resetCoreStubs();
        coreCircuitBreaker.reset();
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    /** Clears stubs and the request journal, keeping the JWKS available for token validation. */
    protected static void resetCoreStubs() {
        core.resetAll();
        core.stubFor(get(urlPathEqualTo(JWKS_PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(TestTokens.jwks())));
    }

    protected ResponseEntity<String> requestBalance(String accountId) {
        return requestBalance(accountId, TestTokens.valid("balance.read"));
    }

    /** Sends the request with the given bearer token, or without an Authorization header when it is null. */
    protected ResponseEntity<String> requestBalance(String accountId, String bearerToken) {
        return api.get().uri("/v1/accounts/{id}/balance", accountId)
                .headers(headers -> {
                    if (bearerToken != null) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }

    protected static MappingBuilder balanceRequest(String accountId) {
        return get(urlPathEqualTo(corePath(accountId)));
    }

    protected static RequestPatternBuilder balanceRequested(String accountId) {
        return getRequestedFor(urlPathEqualTo(corePath(accountId)));
    }

    protected static ResponseDefinitionBuilder balanceBody(String accountId, String balance, String asOf) {
        return aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"accountId":"%s","balance":%s,"currency":"ARS","asOf":"%s"}
                        """.formatted(accountId, balance, asOf));
    }

    protected static String jsonString(ResponseEntity<String> response, String path) {
        return JsonPath.read(response.getBody(), path);
    }

    private static String corePath(String accountId) {
        return "/core/accounts/" + accountId + "/balance";
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }
}
