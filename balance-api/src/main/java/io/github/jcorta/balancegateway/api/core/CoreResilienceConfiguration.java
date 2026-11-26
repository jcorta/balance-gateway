package io.github.jcorta.balancegateway.api.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Resilience4j instances for the core. Their settings live in application.yml under resilience4j.*. */
@Configuration(proxyBeanMethods = false)
public class CoreResilienceConfiguration {

    private static final String CORE = "core";

    @Bean
    CircuitBreaker coreCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(CORE);
    }

    @Bean
    Retry coreRetry(RetryRegistry registry) {
        return registry.retry(CORE);
    }

    @Bean
    TimeLimiter coreTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter(CORE);
    }

    /** Runs each logical request to the core on its own virtual thread so that the deadline can interrupt it. */
    @Bean
    ExecutorService coreCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
