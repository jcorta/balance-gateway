package io.github.jcorta.balancegateway.api.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.jcorta.balancegateway.api.web.ProblemResponseWriter;
import io.github.jcorta.balancegateway.api.web.Problems;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Answers 401 with Problem Details, keeping the WWW-Authenticate challenge that bearer clients rely on. */
@Component
class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerChallenge = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter writer;

    ProblemDetailsAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        bearerChallenge.commence(request, response, authException);
        writer.write(response, Problems.create(request.getRequestURI(), HttpStatus.UNAUTHORIZED, "unauthenticated",
                "Unauthenticated", "A valid access token is required."));
    }
}
