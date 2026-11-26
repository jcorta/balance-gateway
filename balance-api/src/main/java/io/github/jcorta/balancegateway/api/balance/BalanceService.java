package io.github.jcorta.balancegateway.api.balance;

import io.github.jcorta.balancegateway.api.core.CoreBalanceClient;

import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final CoreBalanceClient core;

    public BalanceService(CoreBalanceClient core) {
        this.core = core;
    }

    public BalanceResponse balanceOf(String accountId) {
        return BalanceResponse.current(core.fetchBalance(accountId));
    }
}
