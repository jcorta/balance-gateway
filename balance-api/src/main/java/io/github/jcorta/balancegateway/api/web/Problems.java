package io.github.jcorta.balancegateway.api.web;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Single place where error bodies are built, so that every error response has the same shape. */
public final class Problems {

    private static final String TYPE_PREFIX = "urn:balance-gateway:problem:";

    private Problems() {
    }

    public static ProblemDetail create(String path, HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(path));
        addCorrelationId(problem);
        return problem;
    }

    /** Adds the id of the current request so that a reported error can be traced in the logs of every system. */
    public static void addCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
