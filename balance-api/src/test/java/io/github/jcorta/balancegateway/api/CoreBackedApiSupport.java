package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/** Starts the application on a random port with WireMock standing in for the core. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
abstract class CoreBackedApiSupport {

    @RegisterExtension
    static WireMockExtension core = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void coreProperties(DynamicPropertyRegistry registry) {
        registry.add("balance.core.base-url", core::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected CircuitBreaker coreCircuitBreaker;

    private RestClient api;

    @BeforeEach
    void createClientAndCloseCircuit() {
        coreCircuitBreaker.reset();
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    protected ResponseEntity<String> requestBalance(String accountId) {
        return api.get().uri("/v1/accounts/{id}/balance", accountId).retrieve().toEntity(String.class);
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
}
