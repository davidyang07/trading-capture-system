package com.tradingcapture.observability;

import java.util.regex.Pattern;

/**
 * Shared correlation-ID conventions. The same names are used for the HTTP header,
 * the MDC key rendered into structured logs, and (from Phase 6 on) Kafka headers.
 */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final String KAFKA_HEADER = "x-correlation-id";

    /**
     * What an inbound correlation ID may look like. It is written into every log line, echoed on the
     * response and stored in VARCHAR(64) columns, so anything longer or carrying other characters
     * (newlines that forge log lines, header-splitting characters) is replaced by a fresh ID.
     */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    /** True when an inbound value can be used as the correlation ID as is. */
    public static boolean isSafe(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches();
    }
}
