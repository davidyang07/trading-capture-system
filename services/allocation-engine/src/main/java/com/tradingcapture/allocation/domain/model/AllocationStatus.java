package com.tradingcapture.allocation.domain.model;

/**
 * Persisted allocation state machine (ADR-0006; diagram in docs/architecture/event-flows.md).
 * Phase 7 ingestion writes {@link #RECEIVED} and {@link #INVALID} only; the remaining states
 * belong to Phase 8 orchestration and are declared so the DB CHECK and this enum never need
 * a breaking migration.
 */
public enum AllocationStatus implements CodeEnum {

    RECEIVED,
    INVALID,
    VALIDATED,
    REJECTED,
    EXECUTIONS_ALLOCATED,
    EXECUTIONS_RELEASED,
    COMPLETED,
    FAILED;

    @Override
    public String code() {
        return name();
    }

    /** Terminal states never transition again (PARSE_FAILED is raw-message-only, not here). */
    public boolean isTerminal() {
        return this == INVALID || this == REJECTED || this == COMPLETED || this == FAILED;
    }
}
