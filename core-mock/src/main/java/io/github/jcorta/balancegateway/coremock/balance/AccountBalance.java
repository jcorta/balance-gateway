package io.github.jcorta.balancegateway.coremock.balance;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalance(String accountId, BigDecimal balance, String currency, Instant asOf) {
}
