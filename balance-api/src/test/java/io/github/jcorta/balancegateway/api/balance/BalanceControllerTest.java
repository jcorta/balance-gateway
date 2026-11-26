package io.github.jcorta.balancegateway.api.balance;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.jcorta.balancegateway.api.web.ApiExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

@WebMvcTest(BalanceController.class)
@Import(ApiExceptionHandler.class)
class BalanceControllerTest {

    private static final BalanceResponse BALANCE = new BalanceResponse(
            "ACC-1001", new BigDecimal("152300.75"), "ARS", Instant.parse("2026-11-26T14:03:12.481Z"), false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balances;

    @Test
    void returnsBalanceWithCurrentIndicator() throws Exception {
        when(balances.balanceOf("ACC-1001")).thenReturn(BALANCE);

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1001"))
                .andExpect(jsonPath("$.balance").value(152300.75))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.asOf").value("2026-11-26T14:03:12.481Z"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void forbidsCachingOfBalances() throws Exception {
        when(balances.balanceOf("ACC-1001")).thenReturn(BALANCE);

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectsMalformedAccountIdWithoutCallingTheCore() throws Exception {
        mockMvc.perform(get("/v1/accounts/ACC_1001!/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:invalid-request"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/v1/accounts/ACC_1001!/balance"));

        verifyNoInteractions(balances);
    }

    @Test
    void acceptsAccountIdOfMaximumLength() throws Exception {
        String accountId = "A".repeat(34);
        when(balances.balanceOf(accountId)).thenReturn(BALANCE);

        mockMvc.perform(get("/v1/accounts/" + accountId + "/balance"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAccountIdLongerThanMaximumLength() throws Exception {
        mockMvc.perform(get("/v1/accounts/" + "A".repeat(35) + "/balance"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(balances);
    }

    @Test
    void returnsProblemDetailsWhenAccountIsUnknown() throws Exception {
        when(balances.balanceOf("ACC-9999")).thenThrow(new AccountNotFoundException("ACC-9999"));

        mockMvc.perform(get("/v1/accounts/ACC-9999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:account-not-found"))
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/v1/accounts/ACC-9999/balance"));
    }

    @Test
    void returnsBadGatewayWhenCoreRejectsTheRequest() throws Exception {
        when(balances.balanceOf("ACC-1001"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:core-rejected-request"));
    }

    @Test
    void returnsServiceUnavailableProblemWhenNoBalanceIsKnown() throws Exception {
        when(balances.balanceOf("ACC-1001")).thenThrow(new BalanceUnavailableException("ACC-1001"));

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:balance-unavailable"))
                .andExpect(jsonPath("$.title").value("Balance unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.instance").value("/v1/accounts/ACC-1001/balance"));
    }

    @Test
    void returnsStaleBalanceWithItsOriginalAsOf() throws Exception {
        when(balances.balanceOf("ACC-1001")).thenReturn(new BalanceResponse(
                "ACC-1001", new BigDecimal("152300.75"), "ARS", Instant.parse("2026-11-26T13:00:00Z"), true));

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.asOf").value("2026-11-26T13:00:00Z"));
    }

    @Test
    void hidesInternalDetailsOfUnexpectedErrors() throws Exception {
        when(balances.balanceOf("ACC-1001")).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/v1/accounts/ACC-1001/balance"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalState"))));
    }
}
