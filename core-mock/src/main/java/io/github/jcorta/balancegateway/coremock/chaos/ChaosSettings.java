package io.github.jcorta.balancegateway.coremock.chaos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Failure behaviour applied to balance requests.
 *
 * @param latencyMs        milliseconds added to every request
 * @param errorRatePercent share of requests, from 0 to 100, that fail with HTTP 500
 * @param down             when true, every request fails immediately with HTTP 503
 */
public record ChaosSettings(
        @Min(0) @Max(60_000) int latencyMs,
        @Min(0) @Max(100) int errorRatePercent,
        boolean down) {

    public static final ChaosSettings NONE = new ChaosSettings(0, 0, false);
}
