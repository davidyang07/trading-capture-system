package com.tradingcapture.allocation.domain.model;

/**
 * Persisted allocation state machine (ADR-0006; diagram in docs/architecture/event-flows.md).
 * Phase 7 ingestion writes {@link #RECEIVED} and {@link #INVALID} only; the remaining states
 * belong to Phase 8 orchestration. {@link #REPLACED} and {@link #CANCELLED} mark an allocation
 * that a later Replace/Cancel instruction superseded (its executions were released).
 */
public enum AllocationStatus implements CodeEnum {

    RECEIVED,
    INVALID,
    VALIDATED,
    REJECTED,
    EXECUTIONS_ALLOCATED,
    EXECUTIONS_RELEASED,
    COMPLETED,
    FAILED,
    REPLACED,
    CANCELLED;

    @Override
    public String code() {
        return name();
    }

    /**
     * Terminal states are never advanced by orchestration again (PARSE_FAILED is raw-message-only,
     * not here). {@link #COMPLETED} is terminal for its own orchestration but may still be
     * superseded by a later Replace/Cancel — see {@link #isSupersedable()}.
     */
    public boolean isTerminal() {
        return this == INVALID || this == REJECTED || this == COMPLETED || this == FAILED
                || this == REPLACED || this == CANCELLED;
    }

    /**
     * The live states a Replace/Cancel may supersede: executions are bound
     * ({@link #EXECUTIONS_ALLOCATED}) or the allocation finished ({@link #COMPLETED}), and no other
     * instruction has replaced or cancelled it yet.
     */
    public boolean isSupersedable() {
        return this == EXECUTIONS_ALLOCATED || this == COMPLETED;
    }

    /**
     * Working states from which trading-service may already hold executions bound to this
     * allocation's allocId: the ALLOCATE call can have been made (and its response lost) in
     * {@link #VALIDATED} or {@link #EXECUTIONS_RELEASED}, and it succeeded in
     * {@link #EXECUTIONS_ALLOCATED}.
     */
    public boolean mayHoldExecutions() {
        return this == VALIDATED || this == EXECUTIONS_RELEASED || this == EXECUTIONS_ALLOCATED;
    }
}
