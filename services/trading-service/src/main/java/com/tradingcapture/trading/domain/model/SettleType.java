package com.tradingcapture.trading.domain.model;

/** Settlement type, persisted as the FIX SettlType(63) code. */
public enum SettleType {
    REGULAR("0"),
    CASH("1"),
    NEXT_DAY("2"),
    T_PLUS_2("3"),
    T_PLUS_3("4"),
    T_PLUS_4("5");

    private final String code;

    SettleType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SettleType fromCode(String code) {
        for (SettleType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SettleType code: " + code);
    }
}
