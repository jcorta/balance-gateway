package io.github.jcorta.balancegateway.api.web;

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

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesAWellFormedIdSentByTheCaller() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNone() throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).matches(UUID_PATTERN);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(duringRequest.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "semi;colon", "new\nline", "<script>", "", "tooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"})
    void replacesAMalformedId(String malformed) throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, malformed);
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).matches(UUID_PATTERN);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(duringRequest.get());
    }

    @Test
    void acceptsAnIdOfTheMaximumLength() throws Exception {
        String longest = "a".repeat(64);
        request.addHeader(CorrelationIdFilter.HEADER, longest);

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(longest);
    }

    @Test
    void setsTheResponseHeaderBeforeTheRestOfTheChainRuns() throws Exception {
        AtomicReference<String> headerWhenChainRuns = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> headerWhenChainRuns.set(response.getHeader(CorrelationIdFilter.HEADER)));

        assertThat(headerWhenChainRuns.get()).isNotBlank();
    }

    @Test
    void leavesNoIdInTheLoggingContextAfterTheRequest() throws Exception {
        filter.doFilter(request, response, (req, res) -> { });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void leavesNoIdInTheLoggingContextWhenTheRequestFails() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
