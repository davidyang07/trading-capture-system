package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;

/**
 * A remote dependency call failed at the transport/status level. The message carries only a safe
 * classification (dependency name, failure type, short reason such as an HTTP status) — never a
 * response body, header, access token, or secret. Business verdicts (account inactive, execution
 * not found) are NOT modelled as this exception; they are ordinary 200 results.
 */
public class DependencyCallException extends RuntimeException {

    private final DependencyFailureType type;
    private final String dependency;

    public DependencyCallException(DependencyFailureType type, String dependency, String safeReason) {
        super("[" + dependency + "] " + type + ": " + safeReason);
        this.type = type;
        this.dependency = dependency;
    }

    public DependencyFailureType type() {
        return type;
    }

    public String dependency() {
        return dependency;
    }

    public boolean isRetryable() {
        return type.isRetryable();
    }

    public OrchestrationFailureCategory toCategory() {
        return type.toCategory();
    }
}
