package com.tradingcapture.allocation.validation.logical;

/**
 * The ordered stages of Phase 8 logical (cross-service) validation. Declaration order is the
 * deterministic error-ordering primary key (accounts first, transaction-type prerequisites last).
 */
public enum ValidationStage {
    ACCOUNT,
    INSTRUMENT,
    EXECUTION,
    QUANTITY,
    TRANSACTION_TYPE
}
