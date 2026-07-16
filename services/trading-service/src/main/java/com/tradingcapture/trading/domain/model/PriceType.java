package com.tradingcapture.trading.domain.model;

/**
 * Price type, persisted with the code mapping ported from the reference domain
 * (PERCENTAGE=0, PER_UNIT=1, FIXED_AMOUNT=2).
 */
public enum PriceType {
    PERCENTAGE("0"),
    PER_UNIT("1"),
    FIXED_AMOUNT("2");

    private final String code;

    PriceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PriceType fromCode(String code) {
        for (PriceType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown PriceType code: " + code);
    }
}
