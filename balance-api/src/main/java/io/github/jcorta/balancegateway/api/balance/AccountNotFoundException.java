package io.github.jcorta.balancegateway.api.balance;

/** The core does not know the account. This is an authoritative answer, never a reason to fall back. */
public class AccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}
