package io.github.jcorta.balancegateway.api.balance;

import io.github.jcorta.balancegateway.api.core.CoreUnavailableException;
import io.github.jcorta.balancegateway.api.core.ResilientCoreBalanceClient;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides what a channel receives. A balance obtained from the core during the request is current. When the core
 * is unavailable, the last known balance is returned marked as stale, or the request fails if there is none.
 * Authoritative answers from the core (unknown account, rejected request) never trigger the fallback. See ADR-0003.
 */
@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);
    private static final String FALLBACK_METRIC = "balance.fallback";

    private final ResilientCoreBalanceClient core;
    private final LastKnownBalanceCache lastKnown;
    private final MeterRegistry meters;

    public BalanceService(ResilientCoreBalanceClient core, LastKnownBalanceCache lastKnown, MeterRegistry meters) {
        this.core = core;
        this.lastKnown = lastKnown;
        this.meters = meters;
    }

    public BalanceResponse balanceOf(String accountId) {
        log.info("Balance requested for account {}", accountId);
        try {
            AccountBalance balance = core.fetchBalance(accountId);
            lastKnown.put(balance);
            return BalanceResponse.current(balance);
        } catch (CoreUnavailableException e) {
            return lastKnown.find(accountId)
                    .map(balance -> {
                        meters.counter(FALLBACK_METRIC, "outcome", "stale").increment();
                        log.warn("Core unavailable, serving last known balance for account {} as of {}",
                                accountId, balance.asOf());
                        return BalanceResponse.stale(balance);
                    })
                    .orElseThrow(() -> {
                        meters.counter(FALLBACK_METRIC, "outcome", "unavailable").increment();
                        log.warn("Core unavailable and no recent balance known for account {}", accountId);
                        return new BalanceUnavailableException(accountId);
                    });
        }
    }
}
