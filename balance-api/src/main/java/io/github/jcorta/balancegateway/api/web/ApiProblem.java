package io.github.jcorta.balancegateway.api.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documents the error body in the OpenAPI description; responses are produced as {@code ProblemDetail}. */
@Schema(description = "Problem Details (RFC 9457) with the correlation id of the request")
public record ApiProblem(
        @Schema(example = "urn:balance-gateway:problem:balance-unavailable") String type,
        @Schema(example = "Balance unavailable") String title,
        @Schema(example = "503") int status,
        @Schema(example = "The core banking system is unavailable and no recent balance is known for this account.")
        String detail,
        @Schema(example = "/v1/accounts/ACC-1001/balance") String instance,
        @Schema(description = "Echo of X-Correlation-Id, to quote when reporting the error",
                example = "5f0c6c0e-6f1e-4a4e-9a65-2f0f0f6a8b51") String correlationId) {
}
