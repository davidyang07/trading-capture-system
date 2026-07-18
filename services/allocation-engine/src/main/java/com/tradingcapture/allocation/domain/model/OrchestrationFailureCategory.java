package com.tradingcapture.allocation.domain.model;

/**
 * Why a Phase 8 orchestration attempt failed, and whether it may be retried. ADR-0006's decision
 * stands: retryability is bookkeeping on the allocation row, never a new state. A retryable
 * category keeps the allocation in its working state and schedules {@code next_attempt_at}; a
 * non-retryable category drives a single terminal transition (REJECTED for business rejection,
 * FAILED otherwise). Only the safe classification is persisted — never a payload dump.
 */
public enum OrchestrationFailureCategory implements CodeEnum {

    /** A business rule rejected the instruction (e.g. inactive account, unresolved execution). Terminal → REJECTED. */
    BUSINESS_REJECTION(false),
    /** A downstream service was unreachable/unavailable (connection refused, circuit open, 503). Retryable. */
    DEPENDENCY_UNAVAILABLE(true),
    /** A transient infrastructure fault (timeout, temporary 5xx, optimistic-lock conflict). Retryable. */
    TRANSIENT_INFRASTRUCTURE(true),
    /** Retries exhausted, or a non-retryable orchestration fault that is not a business rejection. Terminal → FAILED. */
    TERMINAL_ORCHESTRATION(false),
    /** A programming/consistency defect (an invariant that should never break). Terminal → FAILED. */
    INVARIANT_VIOLATION(false);

    private final boolean retryable;

    OrchestrationFailureCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public String code() {
        return name();
    }
}
