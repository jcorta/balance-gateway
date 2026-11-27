package io.github.jcorta.balancegateway.api.balance;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits of the last known balance fallback.
 *
 * @param maxAge     how long after being obtained from the core a balance may still be served as stale
 * @param maxEntries maximum number of accounts held
 */
@Validated
@ConfigurationProperties("balance.fallback")
public record FallbackProperties(@NotNull Duration maxAge, @Positive long maxEntries) {
}
