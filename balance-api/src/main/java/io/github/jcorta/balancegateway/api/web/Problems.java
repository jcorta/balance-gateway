package io.github.jcorta.balancegateway.api.web;

import java.net.URI;

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
        return problem;
    }
}
