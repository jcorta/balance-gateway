package io.github.jcorta.balancegateway.api.core;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Logs retries and circuit breaker transitions so that degradation is visible without a debugger. */
@Component
class ResilienceEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventLogger.class);

    ResilienceEventLogger(Retry coreRetry, CircuitBreaker coreCircuitBreaker) {
        coreRetry.getEventPublisher()
                .onRetry(event -> log.warn("Core call failed (attempt {}), retrying in {} ms: {}",
                        event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis(),
                        event.getLastThrowable().toString()))
                .onError(event -> log.warn("Core call failed after {} attempts: {}",
                        event.getNumberOfRetryAttempts(), event.getLastThrowable().toString()));
        coreCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Circuit breaker '{}' changed state: {}",
                        event.getCircuitBreakerName(), event.getStateTransition()));
    }
}
