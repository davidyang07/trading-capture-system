package com.tradingcapture.trading.domain.model;

/** Order type, persisted as the FIX OrdType(40) code. */
public enum OrderType {
    MARKET("1"),
    LIMIT("2");

    private final String code;

    OrderType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static OrderType fromCode(String code) {
        for (OrderType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown OrderType code: " + code);
    }
}
