package com.tradingcapture.allocation.client;

/**
 * The machine-to-machine access token could not be obtained, so the dependency call never left this
 * service. {@link RemoteCallExecutor} maps it like the call's own failure would be mapped: an
 * identity-provider outage is retryable (UNAVAILABLE); a rejected client is a configuration error
 * (UNAUTHORIZED), as a 401 from the dependency itself would be.
 */
public class TokenAcquisitionException extends RuntimeException {

    private final boolean transientFailure;

    public TokenAcquisitionException(String safeReason, boolean transientFailure, Throwable cause) {
        super(safeReason, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
