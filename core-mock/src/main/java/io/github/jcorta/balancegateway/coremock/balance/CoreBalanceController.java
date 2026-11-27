package io.github.jcorta.balancegateway.coremock.balance;

import io.github.jcorta.balancegateway.coremock.chaos.ChaosService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CoreBalanceController {

    private static final Logger log = LoggerFactory.getLogger(CoreBalanceController.class);

    private final AccountRepository accounts;
    private final ChaosService chaos;

    public CoreBalanceController(AccountRepository accounts, ChaosService chaos) {
        this.accounts = accounts;
        this.chaos = chaos;
    }

    @GetMapping("/core/accounts/{accountId}/balance")
    public AccountBalance balance(@PathVariable String accountId) {
        log.info("Balance requested for account {}", accountId);
        chaos.applyToRequest();
        return accounts.findBalance(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown account"));
    }
}
