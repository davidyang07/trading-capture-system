package com.tradingcapture.trading.api.dto;

/** Whitelisted order sort keys — arbitrary property paths are never accepted. */
public enum OrderSortField {
    ORDER_ID("orderId"),
    STATUS("status"),
    PRICE("price"),
    SETTLE_DATE("settleDate"),
    CREATED_AT("createdAt");

    private final String property;

    OrderSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
