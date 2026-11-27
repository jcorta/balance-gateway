package io.github.jcorta.balancegateway.api.core;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the core banking system.
 *
 * @param connectTimeout maximum time to establish a connection
 * @param readTimeout    maximum time to wait for the response of one attempt
 */
@Validated
@ConfigurationProperties("balance.core")
public record CoreProperties(@NotNull URI baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
