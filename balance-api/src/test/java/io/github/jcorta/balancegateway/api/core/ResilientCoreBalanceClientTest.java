package io.github.jcorta.balancegateway.api.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.jcorta.balancegateway.api.balance.AccountBalance;
import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Uses real Resilience4j objects configured like application.yml (with shorter waits and deadline) and a mocked
 * core client, so the composition order is tested without Spring.
 */
@ExtendWith(MockitoExtension.class)
class ResilientCoreBalanceClientTest {

    private static final String ACCOUNT = "ACC-1001";
    private static final AccountBalance BALANCE =
            new AccountBalance(ACCOUNT, new BigDecimal("152300.75"), "ARS", Instant.parse("2026-11-26T14:03:12Z"));

    @Mock
    private CoreBalanceClient core;

    private CircuitBreaker circuitBreaker;
    private ExecutorService executor;
    private ResilientCoreBalanceClient client;

    @BeforeEach
    void createClient() {
        Retry retry = Retry.of("core", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(1), 2))
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());
        circuitBreaker = CircuitBreaker.of("core", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .ignoreExceptions(AccountNotFoundException.class, HttpClientErrorException.class)
                .build());
        TimeLimiter timeLimiter = TimeLimiter.of("core", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(400))
                .cancelRunningFuture(true)
                .build());
        executor = Executors.newVirtualThreadPerTaskExecutor();
        client = new ResilientCoreBalanceClient(core, circuitBreaker, retry, timeLimiter, executor);
    }

    @AfterEach
    void shutDown() {
        executor.close();
        MDC.clear();
    }

    @Test
    void returnsTheBalanceWhenTheCoreAnswers() {
        when(core.fetchBalance(ACCOUNT)).thenReturn(BALANCE);

        assertThat(client.fetchBalance(ACCOUNT)).isEqualTo(BALANCE);

        verify(core, times(1)).fetchBalance(ACCOUNT);
    }

    @Test
    void retriesServerErrorsUntilTheCoreAnswers() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(serverError()).thenThrow(serverError()).thenReturn(BALANCE);

        assertThat(client.fetchBalance(ACCOUNT)).isEqualTo(BALANCE);

        verify(core, times(3)).fetchBalance(ACCOUNT);
    }

    @Test
    void retriesConnectionAndReadTimeouts() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(new ResourceAccessException("read timed out")).thenReturn(BALANCE);

        assertThat(client.fetchBalance(ACCOUNT)).isEqualTo(BALANCE);

        verify(core, times(2)).fetchBalance(ACCOUNT);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(serverError());

        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);

        verify(core, times(3)).fetchBalance(ACCOUNT);
    }

    @Test
    void doesNotRetryWhenTheCoreRejectsTheRequest() {
        when(core.fetchBalance(ACCOUNT))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(HttpClientErrorException.class);

        verify(core, times(1)).fetchBalance(ACCOUNT);
    }

    @Test
    void doesNotRetryWhenTheAccountIsUnknown() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(new AccountNotFoundException(ACCOUNT));

        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(AccountNotFoundException.class);

        verify(core, times(1)).fetchBalance(ACCOUNT);
    }

    @Test
    void doesNotRetryAnUnreadableResponseButReportsTheCoreAsUnavailable() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(new RestClientException("cannot extract response"));

        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);

        verify(core, times(1)).fetchBalance(ACCOUNT);
    }

    @Test
    void doesNotCountClientErrorsAsCoreFailures() {
        when(core.fetchBalance(anyString())).thenThrow(new AccountNotFoundException(ACCOUNT));

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(AccountNotFoundException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensTheCircuitAfterSustainedFailuresAndStopsCallingTheCore() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(serverError());

        // First request: 3 failed attempts. Second request: the 4th and 5th failed attempts open the circuit,
        // so the third attempt is not permitted and is not retried.
        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);
        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(core, times(5)).fetchBalance(ACCOUNT);

        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);
        verify(core, times(5)).fetchBalance(ACCOUNT);
    }

    @Test
    void interruptsTheAttemptAndFailsWithinTheDeadlineWhenTheCoreHangs() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        when(core.fetchBalance(ACCOUNT)).thenAnswer(invocation -> {
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return BALANCE;
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isGreaterThanOrEqualTo(400).isLessThan(2_000);
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void boundsTheWholeRequestIncludingRepetitionsNotJustEachAttempt() {
        // Each attempt fails after 250 ms, within any per-attempt limit. Two attempts already exceed the 400 ms
        // deadline, so the third must never start: without a total limit the request would take over 750 ms.
        when(core.fetchBalance(ACCOUNT)).thenAnswer(invocation -> {
            try {
                Thread.sleep(Duration.ofMillis(250));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new ResourceAccessException("connection reset");
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isBetween(400L, 650L);
        verify(core, times(2)).fetchBalance(ACCOUNT);
    }

    @Test
    void keepsTheCircuitClosedWhileTheFailureRateStaysBelowTheThreshold() {
        AtomicInteger calls = new AtomicInteger();
        when(core.fetchBalance(ACCOUNT)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() <= 6) {
                return BALANCE;
            }
            throw serverError();
        });

        for (int i = 0; i < 6; i++) {
            client.fetchBalance(ACCOUNT);
        }
        // Three failed attempts out of nine recorded calls: 33 percent, below the 50 percent threshold.
        assertThatThrownBy(() -> client.fetchBalance(ACCOUNT)).isInstanceOf(CoreUnavailableException.class);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void passesTheCorrelationIdToTheThreadThatCallsTheCore() {
        AtomicReference<String> seen = new AtomicReference<>();
        when(core.fetchBalance(ACCOUNT)).thenAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return BALANCE;
        });
        MDC.put("correlationId", "abc-123");

        client.fetchBalance(ACCOUNT);

        assertThat(seen.get()).isEqualTo("abc-123");
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
    }
}
