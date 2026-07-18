package com.tradingcapture.allocation.validation.logical;

/**
 * Stable machine-readable codes for Phase 8 logical business-validation failures, each bound to its
 * {@link ValidationStage}. These are business rejections (never dependency/infrastructure failures)
 * and are safe to publish in rejection events.
 */
public enum LogicalValidationCode {

    // Account
    ACCOUNT_NOT_FOUND(ValidationStage.ACCOUNT),
    ACCOUNT_INACTIVE(ValidationStage.ACCOUNT),
    ACCOUNT_UNAUTHORIZED(ValidationStage.ACCOUNT),
    INSUFFICIENT_CASH(ValidationStage.ACCOUNT),
    INSUFFICIENT_SECURITIES(ValidationStage.ACCOUNT),

    // Instrument / reference
    INSTRUMENT_NOT_FOUND(ValidationStage.INSTRUMENT),
    INSTRUMENT_AMBIGUOUS(ValidationStage.INSTRUMENT),
    INSTRUMENT_INACTIVE(ValidationStage.INSTRUMENT),
    EXCHANGE_MISMATCH(ValidationStage.INSTRUMENT),
    CURRENCY_UNSUPPORTED(ValidationStage.INSTRUMENT),
    SETTLEMENT_INCOMPATIBLE(ValidationStage.INSTRUMENT),

    // Execution
    EXECUTION_NOT_FOUND(ValidationStage.EXECUTION),
    EXECUTION_CANCELLED(ValidationStage.EXECUTION),
    EXECUTION_SUPERSEDED(ValidationStage.EXECUTION),
    EXECUTION_ALREADY_ALLOCATED(ValidationStage.EXECUTION),
    EXECUTION_INSTRUMENT_MISMATCH(ValidationStage.EXECUTION),
    EXECUTION_SIDE_MISMATCH(ValidationStage.EXECUTION),
    EXECUTION_NO_QUANTITY(ValidationStage.EXECUTION),
    DUPLICATE_EXECUTION_REF(ValidationStage.EXECUTION),

    // Quantity / consistency
    QUANTITY_NOT_POSITIVE(ValidationStage.QUANTITY),
    QUANTITY_MISMATCH(ValidationStage.QUANTITY),
    QUANTITY_PRECISION(ValidationStage.QUANTITY),
    INSUFFICIENT_EXECUTION_QUANTITY(ValidationStage.QUANTITY),

    // Transaction-type prerequisites
    NEW_CONFLICTING_ALLOCATION(ValidationStage.TRANSACTION_TYPE),
    REPLACE_ORIGINAL_NOT_FOUND(ValidationStage.TRANSACTION_TYPE),
    REPLACE_ORIGINAL_INELIGIBLE(ValidationStage.TRANSACTION_TYPE),
    REPLACE_ACCOUNT_MISMATCH(ValidationStage.TRANSACTION_TYPE),
    REPLACE_INSTRUMENT_MISMATCH(ValidationStage.TRANSACTION_TYPE),
    CANCEL_ORIGINAL_NOT_FOUND(ValidationStage.TRANSACTION_TYPE),
    CANCEL_ORIGINAL_INELIGIBLE(ValidationStage.TRANSACTION_TYPE);

    private final ValidationStage stage;

    LogicalValidationCode(ValidationStage stage) {
        this.stage = stage;
    }

    public ValidationStage stage() {
        return stage;
    }
}
