package io.github.jcorta.balancegateway.api.core;

import io.github.jcorta.balancegateway.api.balance.AccountBalance;
import io.github.jcorta.balancegateway.api.balance.AccountNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * One HTTP call to the core. Failures are not translated here: {@code HttpServerErrorException},
 * {@code HttpClientErrorException} and I/O errors propagate so that the resilience layer can classify them.
 */
@Component
public class CoreBalanceClient {

    private final RestClient restClient;

    public CoreBalanceClient(RestClient coreRestClient) {
        this.restClient = coreRestClient;
    }

    public AccountBalance fetchBalance(String accountId) {
        return restClient.get()
                .uri("/core/accounts/{accountId}/balance", accountId)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                    throw new AccountNotFoundException(accountId);
                })
                .body(AccountBalance.class);
    }
}
