package io.github.jcorta.balancegateway.coremock.chaos;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChaosController.class)
@Import(ChaosService.class)
class ChaosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChaosService chaos;

    @MockitoBean
    private RandomGenerator random;

    @BeforeEach
    void resetChaos() {
        chaos.reset();
    }

    @Test
    void reportsNoChaosByDefault() throws Exception {
        mockMvc.perform(get("/admin/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(0))
                .andExpect(jsonPath("$.errorRatePercent").value(0))
                .andExpect(jsonPath("$.down").value(false));
    }

    @Test
    void appliesNewSettingsWithoutRestart() throws Exception {
        mockMvc.perform(post("/admin/chaos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"latencyMs\":300,\"errorRatePercent\":40,\"down\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/chaos"))
                .andExpect(jsonPath("$.latencyMs").value(300))
                .andExpect(jsonPath("$.errorRatePercent").value(40));
    }

    @Test
    void treatsOmittedFieldsAsNoChaos() throws Exception {
        mockMvc.perform(post("/admin/chaos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"down\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(0))
                .andExpect(jsonPath("$.down").value(true));
    }

    @Test
    void rejectsErrorRateAboveOneHundred() throws Exception {
        mockMvc.perform(post("/admin/chaos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"errorRatePercent\":101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeLatency() throws Exception {
        mockMvc.perform(post("/admin/chaos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"latencyMs\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetRestoresNormalBehaviour() throws Exception {
        chaos.update(new ChaosSettings(500, 100, true));

        mockMvc.perform(delete("/admin/chaos")).andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/chaos"))
                .andExpect(jsonPath("$.latencyMs").value(0))
                .andExpect(jsonPath("$.errorRatePercent").value(0))
                .andExpect(jsonPath("$.down").value(false));
    }
}
