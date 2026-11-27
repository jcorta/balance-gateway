package io.github.jcorta.balancegateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;

import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;
import io.github.jcorta.balancegateway.api.balance.FallbackProperties;
import io.github.jcorta.balancegateway.api.core.CoreProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Checks that the values in application.yml, without test overrides, are the ones documented in the plan. */
@SpringBootTest
class ResilienceConfigurationIT {

    @Autowired
    private CoreProperties core;

    @Autowired
    private FallbackProperties fallback;

    @Autowired
    private Retry retry;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @Autowired
    private TimeLimiter timeLimiter;

    @Test
    void usesTheDocumentedTimeoutsAndFallbackLimits() {
        assertThat(core.connectTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(core.readTimeout()).isEqualTo(Duration.ofMillis(700));
        assertThat(timeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(2));
        assertThat(timeLimiter.getTimeLimiterConfig().shouldCancelRunningFuture()).isTrue();
        assertThat(fallback.maxAge()).isEqualTo(Duration.ofMinutes(10));
        assertThat(fallback.maxEntries()).isEqualTo(100_000);
    }

    @Test
    void retriesOnlyServerErrorsAndIoFailuresUpToThreeAttempts() {
        RetryConfig config = retry.getRetryConfig();

        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getExceptionPredicate().test(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", null, null, null)))
                .isTrue();
        assertThat(config.getExceptionPredicate().test(new ResourceAccessException("timeout"))).isTrue();
        assertThat(config.getExceptionPredicate().test(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "x", null, null, null)))
                .isFalse();
        assertThat(config.getExceptionPredicate().test(new AccountNotFoundException("ACC-1"))).isFalse();
    }

    @Test
    void waitsLongerBeforeEachSuccessiveRetry() {
        var interval = retry.getRetryConfig().<Object>getIntervalBiFunction();
        Either<Throwable, Object> failed = Either.left(new ResourceAccessException("timeout"));

        long longestFirstWait = IntStream.range(0, 200).mapToLong(i -> interval.apply(1, failed)).max().orElseThrow();
        long shortestThirdWait = IntStream.range(0, 200).mapToLong(i -> interval.apply(3, failed)).min().orElseThrow();

        assertThat(shortestThirdWait).isGreaterThan(longestFirstWait);
    }

    @Test
    void opensTheCircuitAtHalfTheRecentCallsAfterAtLeastFiveCalls() {
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(15_000L);
    }

    @Test
    void ignoresClientErrorsWhenRecordingFailures() {
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getRecordExceptionPredicate().test(new ResourceAccessException("timeout"))).isTrue();
        assertThat(config.getIgnoreExceptionPredicate().test(new AccountNotFoundException("ACC-1"))).isTrue();
        assertThat(config.getIgnoreExceptionPredicate().test(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "x", null, null, null)))
                .isTrue();
    }
}
