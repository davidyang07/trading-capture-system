package com.tradingcapture.events;

/**
 * Kafka header names mirroring the {@link EventMetadata} envelope for header-only consumers
 * (docs/contracts/event-catalog.md). The correlation header value matches
 * {@code com.tradingcapture.observability.CorrelationId#KAFKA_HEADER}; the constant is not
 * imported to keep event-contracts free of a lib dependency (asserted equal by test).
 */
public final class EventHeaders {

    public static final String EVENT_ID = "x-event-id";
    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String CAUSATION_ID = "x-causation-id";

    private EventHeaders() {
    }
}
