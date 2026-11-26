package io.github.jcorta.balancegateway.coremock.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.random.RandomGenerator;

import io.github.jcorta.balancegateway.coremock.chaos.ChaosController;
import io.github.jcorta.balancegateway.coremock.chaos.ChaosService;
import io.github.jcorta.balancegateway.coremock.chaos.ChaosSettings;
import io.github.jcorta.balancegateway.coremock.web.CorrelationIdFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CoreBalanceController.class, ChaosController.class})
@Import({AccountRepository.class, ChaosService.class, CorrelationIdFilter.class})
class CoreBalanceControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T14:03:12Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChaosService chaos;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private RandomGenerator random;

    @BeforeEach
    void resetChaos() {
        chaos.reset();
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void returnsBalanceForKnownAccount() throws Exception {
        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1001"))
                .andExpect(jsonPath("$.balance").value(152300.75))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.asOf").value("2026-11-26T14:03:12Z"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mockMvc.perform(get("/core/accounts/ACC-9999/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsServiceUnavailableWhenCoreIsDown() throws Exception {
        chaos.update(new ChaosSettings(0, 0, true));

        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsServerErrorWhenRandomDrawIsBelowErrorRate() throws Exception {
        chaos.update(new ChaosSettings(0, 50, false));
        when(random.nextInt(100)).thenReturn(49);

        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void returnsBalanceWhenRandomDrawIsAtOrAboveErrorRate() throws Exception {
        chaos.update(new ChaosSettings(0, 50, false));
        when(random.nextInt(100)).thenReturn(50);

        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isOk());
    }

    @Test
    void addsConfiguredLatencyToEachRequest() throws Exception {
        chaos.update(new ChaosSettings(200, 0, false));

        long start = System.nanoTime();
        mockMvc.perform(get("/core/accounts/ACC-1001/balance")).andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(200);
    }

    @Test
    void appliesChaosSetThroughAdminEndpointToSubsequentBalanceRequests() throws Exception {
        mockMvc.perform(post("/admin/chaos").contentType(APPLICATION_JSON).content("{\"down\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void answersNormallyAgainAfterChaosIsResetThroughAdminEndpoint() throws Exception {
        mockMvc.perform(post("/admin/chaos").contentType(APPLICATION_JSON).content("{\"down\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/chaos")).andExpect(status().isNoContent());

        mockMvc.perform(get("/core/accounts/ACC-1001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1001"));
    }

    @Test
    void failsWithoutDelayWhenCoreIsDownDespiteConfiguredLatency() throws Exception {
        chaos.update(new ChaosSettings(5_000, 0, true));

        long start = System.nanoTime();
        mockMvc.perform(get("/core/accounts/ACC-1001/balance")).andExpect(status().isServiceUnavailable());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1_000);
    }
}
