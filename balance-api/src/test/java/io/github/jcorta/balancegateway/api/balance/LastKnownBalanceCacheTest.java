package io.github.jcorta.balancegateway.api.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LastKnownBalanceCacheTest {

    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final AtomicLong nanos = new AtomicLong();
    private LastKnownBalanceCache cache;

    @BeforeEach
    void createCache() {
        cache = new LastKnownBalanceCache(MAX_AGE, 100, nanos::get);
    }

    @Test
    void returnsTheBalanceStoredForTheAccount() {
        AccountBalance stored = balance("ACC-1001", "100.00");
        cache.put(stored);

        assertThat(cache.find("ACC-1001")).contains(stored);
    }

    @Test
    void returnsNothingForAnAccountNeverStored() {
        assertThat(cache.find("ACC-1002")).isEmpty();
    }

    @Test
    void returnsTheBalanceUntilMaxAgeHasElapsed() {
        cache.put(balance("ACC-1001", "100.00"));

        nanos.addAndGet(MAX_AGE.minusSeconds(1).toNanos());

        assertThat(cache.find("ACC-1001")).isPresent();
    }

    @Test
    void dropsTheBalanceOnceMaxAgeHasElapsed() {
        cache.put(balance("ACC-1001", "100.00"));

        nanos.addAndGet(MAX_AGE.plusSeconds(1).toNanos());

        assertThat(cache.find("ACC-1001")).isEmpty();
    }

    @Test
    void restartsTheAgeWhenANewerBalanceIsStored() {
        cache.put(balance("ACC-1001", "100.00"));
        nanos.addAndGet(MAX_AGE.minusSeconds(1).toNanos());
        AccountBalance newer = balance("ACC-1001", "250.00");
        cache.put(newer);

        nanos.addAndGet(MAX_AGE.minusSeconds(1).toNanos());

        assertThat(cache.find("ACC-1001")).contains(newer);
    }

    private static AccountBalance balance(String accountId, String amount) {
        return new AccountBalance(accountId, new BigDecimal(amount), "ARS", Instant.parse("2026-11-26T14:03:12Z"));
    }
}
