package com.tradingcapture.allocation.domain.model;

/** FIX tag 71 (AllocTransType), persisted as its FIX wire code (ADR-0014). */
public enum AllocTransType implements CodeEnum {

    NEW("0"),
    REPLACE("1"),
    CANCEL("2");

    private final String code;

    AllocTransType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    /** Resolves a FIX wire code; returns null for codes outside the supported domain. */
    public static AllocTransType fromCode(String code) {
        for (AllocTransType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
