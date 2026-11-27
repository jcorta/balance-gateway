package io.github.jcorta.balancegateway.api.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.jcorta.balancegateway.api.web.ProblemResponseWriter;
import io.github.jcorta.balancegateway.api.web.Problems;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Answers 403 with Problem Details when the token is valid but does not carry the required scope. */
@Component
class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    ProblemDetailsAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, Problems.create(request.getRequestURI(), HttpStatus.FORBIDDEN, "forbidden",
                "Forbidden", "The client is not allowed to perform this operation."));
    }
}
