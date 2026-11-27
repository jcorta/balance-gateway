package io.github.jcorta.balancegateway.api.balance;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * In-memory store of the last balance obtained from the core for each account. Entries expire a fixed time after
 * they were obtained, not after the core's {@code asOf}, so the age limit measures how long the gateway has gone
 * without a fresh answer.
 */
public class LastKnownBalanceCache {

    private final Cache<String, AccountBalance> balances;

    public LastKnownBalanceCache(Duration maxAge, long maxEntries, Ticker ticker) {
        this.balances = Caffeine.newBuilder()
                .expireAfterWrite(maxAge)
                .maximumSize(maxEntries)
                .ticker(ticker)
                .build();
    }

    public void put(AccountBalance balance) {
        balances.put(balance.accountId(), balance);
    }

    public Optional<AccountBalance> find(String accountId) {
        return Optional.ofNullable(balances.getIfPresent(accountId));
    }
}
