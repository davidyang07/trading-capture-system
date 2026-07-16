package com.tradingcapture.observability;

/**
 * Shared correlation-ID conventions. The same names are used for the HTTP header,
 * the MDC key rendered into structured logs, and (from Phase 6 on) Kafka headers.
 */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final String KAFKA_HEADER = "x-correlation-id";

    private CorrelationId() {
    }
}
