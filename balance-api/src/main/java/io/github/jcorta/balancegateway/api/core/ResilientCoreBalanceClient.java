package io.github.jcorta.balancegateway.api.core;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.github.jcorta.balancegateway.api.balance.AccountBalance;
import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Calls the core with a total deadline, retries and a circuit breaker, composed as
 * {@code TimeLimiter(Retry(CircuitBreaker(call)))}. See ADR-0002.
 *
 * <p>Failures are reduced to three outcomes: the balance, {@link AccountNotFoundException} or another client error
 * (authoritative, never retried), and {@link CoreUnavailableException} (the caller may fall back).
 */
@Component
public class ResilientCoreBalanceClient {

    private final CoreBalanceClient core;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public ResilientCoreBalanceClient(CoreBalanceClient core, CircuitBreaker coreCircuitBreaker, Retry coreRetry,
            TimeLimiter coreTimeLimiter, ExecutorService coreCallExecutor) {
        this.core = core;
        this.circuitBreaker = coreCircuitBreaker;
        this.retry = coreRetry;
        this.timeLimiter = coreTimeLimiter;
        this.executor = coreCallExecutor;
    }

    public AccountBalance fetchBalance(String accountId) {
        // The circuit breaker is innermost so that it records every attempt; once it is open, the retry receives
        // CallNotPermittedException, which is not retryable, and gives up at once.
        Supplier<AccountBalance> attempt = () -> core.fetchBalance(accountId);
        Supplier<AccountBalance> withBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, attempt);
        Supplier<AccountBalance> withRetry = Retry.decorateSupplier(retry, withBreaker);

        // The MDC does not cross threads: copy it so that the correlation id reaches the logs and the outbound call.
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Callable<AccountBalance> task = () -> callWithMdc(mdc, withRetry);
        try {
            // Future from ExecutorService.submit: cancel(true) interrupts the attempt when the deadline expires.
            // CompletableFuture.cancel does not interrupt its thread.
            return timeLimiter.executeFutureSupplier(() -> executor.submit(task));
        } catch (Exception e) {
            throw translate(e);
        }
    }

    private static AccountBalance callWithMdc(Map<String, String> mdc, Supplier<AccountBalance> call) {
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
        try {
            return call.get();
        } finally {
            MDC.clear();
        }
    }

    private static RuntimeException translate(Exception failure) {
        Throwable cause = failure instanceof ExecutionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return switch (cause) {
            case AccountNotFoundException notFound -> notFound;
            case HttpClientErrorException rejected -> rejected;
            case InterruptedException interrupted -> {
                Thread.currentThread().interrupt();
                yield new CoreUnavailableException("Interrupted while waiting for the core", interrupted);
            }
            case TimeoutException timeout -> new CoreUnavailableException("Deadline for the core call exceeded", timeout);
            case CallNotPermittedException open -> new CoreUnavailableException("Circuit breaker is open", open);
            case RestClientException failed -> new CoreUnavailableException("Call to the core failed", failed);
            case RuntimeException unexpected -> unexpected;
            default -> new IllegalStateException("Unexpected failure calling the core", cause);
        };
    }
}
