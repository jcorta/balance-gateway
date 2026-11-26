package io.github.jcorta.balancegateway.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BalanceResilienceIT {

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

    private RestClient api;

    @BeforeEach
    void createClient() {
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    @Test
    void returnsBalanceReportedByTheCore() {
        core.stubFor(get(urlPathEqualTo("/core/accounts/ACC-1001/balance")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"accountId":"ACC-1001","balance":152300.75,"currency":"ARS","asOf":"2026-11-26T14:03:12.481Z"}
                        """)));

        ResponseEntity<String> response = api.get().uri("/v1/accounts/ACC-1001/balance").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.getBody(), "$.accountId")).isEqualTo("ACC-1001");
        assertThat(JsonPath.<String>read(response.getBody(), "$.currency")).isEqualTo("ARS");
        assertThat(response.getBody()).contains("\"balance\":152300.75");
        assertThat(JsonPath.<String>read(response.getBody(), "$.asOf")).isEqualTo("2026-11-26T14:03:12.481Z");
        assertThat(JsonPath.<Boolean>read(response.getBody(), "$.stale")).isFalse();
    }

    @Test
    void rejectsMalformedAccountIdWithoutCallingTheCore() {
        ResponseEntity<String> response = api.get().uri("/v1/accounts/{id}/balance", "ACC_1001!").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type"))
                .isEqualTo("urn:balance-gateway:problem:invalid-request");
        core.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void returnsNotFoundWithoutRepeatingTheCallWhenCoreDoesNotKnowTheAccount() {
        core.stubFor(get(urlPathEqualTo("/core/accounts/ACC-9999/balance")).willReturn(aResponse().withStatus(404)));

        ResponseEntity<String> response = api.get().uri("/v1/accounts/ACC-9999/balance").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type"))
                .isEqualTo("urn:balance-gateway:problem:account-not-found");
        core.verify(1, getRequestedFor(urlPathEqualTo("/core/accounts/ACC-9999/balance")));
    }
}
