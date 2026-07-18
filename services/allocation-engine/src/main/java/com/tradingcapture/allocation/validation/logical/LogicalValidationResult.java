package com.tradingcapture.allocation.validation.logical;

import com.tradingcapture.allocation.client.DependencyCallException;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;

import java.util.List;

/**
 * The typed outcome of logical validation, explicitly separating a business rejection from a
 * dependency failure, a terminal failure, and an invariant/programming defect — so the future
 * orchestration driver can call the correct Increment-2 bookkeeping (reject vs. schedule-retry vs.
 * fail-terminally). Dependency failures are mapped from a {@link DependencyCallException} rather
 * than mixed into the business-error list.
 */
public sealed interface LogicalValidationResult
        permits LogicalValidationResult.Valid,
        LogicalValidationResult.BusinessRejected,
        LogicalValidationResult.DependencyUnavailable,
        LogicalValidationResult.DependencyTerminal,
        LogicalValidationResult.Invariant {

    boolean isValid();

    static LogicalValidationResult valid() {
        return new Valid();
    }

    static LogicalValidationResult rejected(List<LogicalValidationError> errors) {
        return new BusinessRejected(List.copyOf(errors));
    }

    /** Map a remote failure to a retryable or terminal dependency outcome (never a business rejection). */
    static LogicalValidationResult fromDependencyFailure(DependencyCallException failure) {
        return failure.isRetryable()
                ? new DependencyUnavailable(failure.toCategory(), failure.getMessage())
                : new DependencyTerminal(failure.toCategory(), failure.getMessage());
    }

    /** Passed every stage. */
    record Valid() implements LogicalValidationResult {
        @Override
        public boolean isValid() {
            return true;
        }
    }

    /** One or more business rules rejected the instruction (terminal — REJECTED). */
    record BusinessRejected(List<LogicalValidationError> errors) implements LogicalValidationResult {
        @Override
        public boolean isValid() {
            return false;
        }
    }

    /** A dependency was unavailable/timed-out/5xx (retryable — schedule the next attempt). */
    record DependencyUnavailable(OrchestrationFailureCategory category, String summary)
            implements LogicalValidationResult {
        @Override
        public boolean isValid() {
            return false;
        }
    }

    /** A dependency failed permanently (auth/config/malformed) — terminal FAILED. */
    record DependencyTerminal(OrchestrationFailureCategory category, String summary)
            implements LogicalValidationResult {
        @Override
        public boolean isValid() {
            return false;
        }
    }

    /** An invariant/programming defect (should never happen) — terminal FAILED. */
    record Invariant(String summary) implements LogicalValidationResult {
        @Override
        public boolean isValid() {
            return false;
        }
    }
}
