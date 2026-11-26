package io.github.jcorta.balancegateway.api.core;

/** The core could not provide a balance: server error, no answer in time, or calls are being blocked. */
public class CoreUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
