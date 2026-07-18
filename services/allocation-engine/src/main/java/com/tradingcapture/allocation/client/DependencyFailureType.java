package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;

/**
 * Transport/status classification of a failed remote dependency call (never a business verdict:
 * an inactive account or unresolved execution is a 200 per-item result, not one of these). The
 * {@link #isRetryable()} flag drives Resilience4j retry and circuit-breaker recording, and
 * {@link #toCategory()} maps to the persisted {@link OrchestrationFailureCategory} so the future
 * orchestration driver can schedule a retry or escalate to a terminal failure.
 */
public enum DependencyFailureType {

    /** Connection refused / service unavailable (503) / circuit open. Retryable. */
    UNAVAILABLE(true),
    /** Connect or read timeout. Retryable. */
    TIMEOUT(true),
    /** A 5xx server error (other than 503). Retryable. */
    SERVER_ERROR(true),
    /** 401/403 — a credential/audience/scope configuration problem. Not retryable. */
    UNAUTHORIZED(false),
    /** Other 4xx — a permanent malformed/unsupported request shape. Not retryable. */
    BAD_REQUEST(false),
    /** The response body could not be parsed into the expected contract. Not retryable. */
    MALFORMED_RESPONSE(false),
    /** Any other unexpected failure. Not retryable (fail safe rather than loop). */
    UNEXPECTED(false);

    private final boolean retryable;

    DependencyFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** Low-cardinality metric/outcome tag. */
    public String outcome() {
        return name().toLowerCase();
    }

    public OrchestrationFailureCategory toCategory() {
        return switch (this) {
            case UNAVAILABLE -> OrchestrationFailureCategory.DEPENDENCY_UNAVAILABLE;
            case TIMEOUT, SERVER_ERROR -> OrchestrationFailureCategory.TRANSIENT_INFRASTRUCTURE;
            case UNAUTHORIZED, BAD_REQUEST, MALFORMED_RESPONSE, UNEXPECTED ->
                    OrchestrationFailureCategory.TERMINAL_ORCHESTRATION;
        };
    }
}
