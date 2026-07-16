package com.tradingcapture.trading.domain.model;

/**
 * Order lifecycle status, persisted as the FIX OrdStatus(39) code. Ported from the reference
 * domain (deployment-tcs-exec-backend) with the same code mapping; the status itself is
 * always derived server-side from the order's active executed quantity, never accepted
 * from a client.
 */
public enum OrderStatus {
    NEW("0"),
    PARTIALLY_FILLED("1"),
    FILLED("2");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus code: " + code);
    }
}
