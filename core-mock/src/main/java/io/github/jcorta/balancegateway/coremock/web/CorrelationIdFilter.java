package io.github.jcorta.balancegateway.coremock.web;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exposes the correlation id sent by the caller to the log entries of the request. */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    // Restricted character set: the value is written to the logs.
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        boolean valid = id != null && VALID.matcher(id).matches();
        if (valid) {
            MDC.put(MDC_KEY, id);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (valid) {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
