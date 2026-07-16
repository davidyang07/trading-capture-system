package com.tradingcapture.trading.domain.model;

/** Miscellaneous-fee basis, persisted with the FIX MiscFeeBasis(891)-style codes. */
public enum MiscFeeBasis {
    ABSOLUTE("0"),
    PER_UNIT("1"),
    PERCENTAGE("2");

    private final String code;

    MiscFeeBasis(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MiscFeeBasis fromCode(String code) {
        for (MiscFeeBasis value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown MiscFeeBasis code: " + code);
    }
}
