package io.github.jcorta.balancegateway.api.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;
import io.github.jcorta.balancegateway.api.balance.BalanceUnavailableException;

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
        ProblemDetail problem = Problems.create(path, HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                "The request contains an invalid parameter.");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Problems.addCorrelationId(problem);
        }
        return response;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(HttpServletRequest request) {
        return Problems.create(request.getRequestURI(), HttpStatus.NOT_FOUND, "account-not-found", "Account not found",
                "The core banking system does not know this account.");
    }

    @ExceptionHandler(BalanceUnavailableException.class)
    ProblemDetail balanceUnavailable(HttpServletRequest request) {
        return Problems.create(request.getRequestURI(), HttpStatus.SERVICE_UNAVAILABLE, "balance-unavailable", "Balance unavailable",
                "The core banking system is unavailable and no recent balance is known for this account.");
    }

    @ExceptionHandler(HttpClientErrorException.class)
    ProblemDetail coreRejectedRequest(HttpClientErrorException e, HttpServletRequest request) {
        log.error("Core rejected the request with status {}", e.getStatusCode());
        return Problems.create(request.getRequestURI(), HttpStatus.BAD_GATEWAY, "core-rejected-request",
                "Core rejected the request", "The core banking system rejected the request.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error", e);
        return Problems.create(request.getRequestURI(), HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "An unexpected error occurred.");
    }
}
