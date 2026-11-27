package io.github.jcorta.balancegateway.api.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.jcorta.balancegateway.api.balance.BalanceController;
import io.github.jcorta.balancegateway.api.balance.BalanceResponse;
import io.github.jcorta.balancegateway.api.balance.BalanceService;
import io.github.jcorta.balancegateway.api.web.ApiExceptionHandler;
import io.github.jcorta.balancegateway.api.web.ProblemResponseWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(BalanceController.class)
@Import({SecurityConfiguration.class, ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class, ProblemResponseWriter.class, ApiExceptionHandler.class})
class BalanceSecurityTest {

    private static final String PATH = "/v1/accounts/ACC-1001/balance";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balances;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void stubBalance() {
        when(balances.balanceOf("ACC-1001")).thenReturn(new BalanceResponse(
                "ACC-1001", new BigDecimal("1.00"), "ARS", Instant.parse("2026-11-26T14:03:12Z"), false));
    }

    @Test
    void rejectsARequestWithoutToken() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:unauthenticated"))
                .andExpect(jsonPath("$.title").value("Unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value(PATH));

        verifyNoInteractions(balances);
    }

    @Test
    void rejectsATokenThatTheDecoderDoesNotAccept() throws Exception {
        when(jwtDecoder.decode("tampered")).thenThrow(new BadJwtException("signature mismatch"));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer tampered"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:unauthenticated"))
                .andExpect(content().string(not(containsString("signature"))));

        verifyNoInteractions(balances);
    }

    @Test
    void forbidsAValidTokenWithoutTheBalanceReadScope() throws Exception {
        mockMvc.perform(get(PATH).with(tokenWithScope("reporting.read")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:balance-gateway:problem:forbidden"))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value(PATH));

        verifyNoInteractions(balances);
    }

    @Test
    void forbidsAValidTokenWithoutAnyScope() throws Exception {
        mockMvc.perform(get(PATH).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAValidTokenWithTheBalanceReadScope() throws Exception {
        mockMvc.perform(get(PATH).with(tokenWithScope("balance.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1001"));
    }

    @Test
    void allowsATokenCarryingTheBalanceReadScopeAmongOthers() throws Exception {
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.claim("scope", List.of("reporting.read", "balance.read")))))
                .andExpect(status().isOk());
    }

    @Test
    void deniesEveryPathThatIsNotPartOfTheApi() throws Exception {
        mockMvc.perform(get("/internal/anything").with(tokenWithScope("balance.read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/anything"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor tokenWithScope(String scope) {
        return jwt().jwt(token -> token.claim("scope", scope));
    }
}
