package com.tradingcapture.trading.domain.service;

import java.util.UUID;

/** Business identifier generation (formats ported from the reference: {@code exec_<uuid>}). */
public final class TradingIds {

    private TradingIds() {
    }

    public static String newOrderId() {
        return "ORD-" + UUID.randomUUID();
    }

    public static String newExecutionInternalId() {
        return "exec_" + UUID.randomUUID();
    }

    public static String newExecId() {
        return "EXEC-" + UUID.randomUUID();
    }
}
