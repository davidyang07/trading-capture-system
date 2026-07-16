package com.tradingcapture.trading.api.dto;

/** Whitelisted execution sort keys. */
public enum ExecutionSortField {
    EXEC_ID("execId"),
    TRANSACT_TIME("transactTime"),
    TRADE_DATE("tradeDate"),
    CREATED_AT("createdAt");

    private final String property;

    ExecutionSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
