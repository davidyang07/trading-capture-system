package com.tradingcapture.trading.domain.model;

/** Order side, persisted as the FIX Side(54) code. */
public enum Side {
    BUY("1"),
    SELL("2");

    private final String code;

    Side(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Side fromCode(String code) {
        for (Side value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Side code: " + code);
    }
}
