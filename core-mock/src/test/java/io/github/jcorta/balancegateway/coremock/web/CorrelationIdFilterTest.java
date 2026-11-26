package io.github.jcorta.balancegateway.coremock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void exposesAWellFormedCallerIdToTheLogsOfTheRequest() throws Exception {
        request.addHeader("X-Correlation-Id", "abc-123");
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get("correlationId")));

        assertThat(duringRequest.get()).isEqualTo("abc-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "semi;colon", "new\nline", "<script>", ""})
    void ignoresAMalformedId(String malformed) throws Exception {
        request.addHeader("X-Correlation-Id", malformed);
        AtomicReference<String> duringRequest = new AtomicReference<>("unset");

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get("correlationId")));

        assertThat(duringRequest.get()).isNull();
    }

    @Test
    void ignoresAnIdLongerThanSixtyFourCharacters() throws Exception {
        request.addHeader("X-Correlation-Id", "a".repeat(65));
        AtomicReference<String> duringRequest = new AtomicReference<>("unset");

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get("correlationId")));

        assertThat(duringRequest.get()).isNull();
    }

    @Test
    void leavesNoIdInTheLoggingContextAfterTheRequestEvenWhenItFails() {
        request.addHeader("X-Correlation-Id", "abc-123");

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
