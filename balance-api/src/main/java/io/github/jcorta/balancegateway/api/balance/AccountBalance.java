package io.github.jcorta.balancegateway.api.balance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A balance as reported by the core.
 *
 * @param asOf the instant at which the core produced the balance
 */
public record AccountBalance(String accountId, BigDecimal balance, String currency, Instant asOf) {
}
