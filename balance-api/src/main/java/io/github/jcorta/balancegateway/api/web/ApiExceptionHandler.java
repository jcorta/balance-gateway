package io.github.jcorta.balancegateway.api.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps failures to RFC 9457 Problem Details. Exception messages and stack traces are logged, never returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = problem(path, HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                "The request contains an invalid parameter.");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(HttpServletRequest request) {
        return problem(request.getRequestURI(), HttpStatus.NOT_FOUND, "account-not-found", "Account not found",
                "The core banking system does not know this account.");
    }

    @ExceptionHandler(HttpClientErrorException.class)
    ProblemDetail coreRejectedRequest(HttpClientErrorException e, HttpServletRequest request) {
        log.error("Core rejected the request with status {}", e.getStatusCode());
        return problem(request.getRequestURI(), HttpStatus.BAD_GATEWAY, "core-rejected-request",
                "Core rejected the request", "The core banking system rejected the request.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error", e);
        return problem(request.getRequestURI(), HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "An unexpected error occurred.");
    }

    private static ProblemDetail problem(String path, HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:balance-gateway:problem:" + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(path));
        return problem;
    }
}
