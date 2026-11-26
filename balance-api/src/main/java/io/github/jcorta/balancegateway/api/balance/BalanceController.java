package io.github.jcorta.balancegateway.api.balance;

import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
public class BalanceController {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final BalanceService balances;

    public BalanceController(BalanceService balances) {
        this.balances = balances;
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get the balance of an account",
            description = "Returns the balance reported by the core. When the core is unavailable, returns the last "
                    + "known balance with stale=true and its original asOf, if one is known.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance, current or stale"),
            @ApiResponse(responseCode = "400", description = "Malformed account identifier",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The core does not know the account",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "The core rejected the request",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<BalanceResponse> balance(
            @Parameter(description = "Account identifier", example = "ACC-1001")
            @PathVariable @Pattern(regexp = "[A-Za-z0-9-]{1,34}") String accountId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(balances.balanceOf(accountId));
    }
}
