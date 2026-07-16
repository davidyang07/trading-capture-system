package com.tradingcapture.trading.domain.model;

/**
 * Execution event type, persisted as the FIX ExecType(150) code. TRADE starts a chain;
 * TRADE_CORRECT and TRADE_CANCEL are append-only amendments linked to their target via
 * {@code exec_ref_id} and to the chain start via {@code root_execution_id}.
 */
public enum ExecutionType {
    TRADE("F"),
    TRADE_CORRECT("G"),
    TRADE_CANCEL("H");

    private final String code;

    ExecutionType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ExecutionType fromCode(String code) {
        for (ExecutionType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ExecutionType code: " + code);
    }
}
