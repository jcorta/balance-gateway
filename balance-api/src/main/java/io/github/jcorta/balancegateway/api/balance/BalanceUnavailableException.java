package io.github.jcorta.balancegateway.api.balance;

/** The core is unavailable and no sufficiently recent balance is known for the account. */
public class BalanceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BalanceUnavailableException(String accountId) {
        super("No balance available for account " + accountId);
    }
}
