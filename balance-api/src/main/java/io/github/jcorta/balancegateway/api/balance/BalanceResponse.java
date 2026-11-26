package io.github.jcorta.balancegateway.api.balance;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account balance and whether it is current")
public record BalanceResponse(
        @Schema(example = "ACC-1001") String accountId,
        @Schema(example = "152300.75") BigDecimal balance,
        @Schema(example = "ARS") String currency,
        @Schema(description = "Instant at which the core produced the balance; kept when the balance is served stale",
                example = "2026-11-26T14:03:12.481Z") Instant asOf,
        @Schema(description = "True when the balance is the last known one and was not obtained from the core during this request")
        boolean stale) {

    static BalanceResponse current(AccountBalance balance) {
        return new BalanceResponse(balance.accountId(), balance.balance(), balance.currency(), balance.asOf(), false);
    }
}
