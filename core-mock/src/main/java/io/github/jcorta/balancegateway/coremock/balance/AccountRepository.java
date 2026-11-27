package io.github.jcorta.balancegateway.coremock.balance;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private record Holding(BigDecimal balance, String currency) {
    }

    private static final Map<String, Holding> HOLDINGS = Map.of(
            "ACC-1001", new Holding(new BigDecimal("152300.75"), "ARS"),
            "ACC-1002", new Holding(new BigDecimal("8420.10"), "ARS"),
            "ACC-1003", new Holding(new BigDecimal("25000.00"), "USD"),
            "ACC-1004", new Holding(new BigDecimal("310.55"), "USD"));

    private final Clock clock;

    public AccountRepository(Clock clock) {
        this.clock = clock;
    }

    /** Balances are fixed; asOf is the time of the read, as a real core would report it. */
    public Optional<AccountBalance> findBalance(String accountId) {
        return Optional.ofNullable(HOLDINGS.get(accountId))
                .map(holding -> new AccountBalance(accountId, holding.balance(), holding.currency(), clock.instant()));
    }
}
