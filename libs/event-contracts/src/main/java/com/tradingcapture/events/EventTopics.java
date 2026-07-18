package com.tradingcapture.events;

import java.util.List;

/**
 * Topic catalog of record in code form — must stay synchronized with
 * {@code docs/contracts/event-catalog.md} (checked by {@code TopicConventionsTest}).
 *
 * <p>Conventions (ADR-0004): topic names are {@code tcs.<owning-domain>.<event>.v<major>},
 * one event type per topic (TopicNameStrategy, subject {@code <topic>-value}), BACKWARD
 * compatibility per subject, message key = aggregate id. Retry/DLT suffixes apply to inbound
 * processing topics only.</p>
 */
public final class EventTopics {

    /** Inbound raw FIX 4.4 AllocationInstruction strings (owner: upstream FIX gateway; consumer: allocation-engine). */
    public static final String FIX_ALLOCATION_INSTRUCTION = "tcs.fix.allocation-instruction.v1";

    // Outbound domain events (owner/producer: allocation-engine, via the transactional outbox).
    public static final String ALLOCATION_RECEIVED = "tcs.allocation.allocation-received.v1";
    public static final String ALLOCATION_VALIDATED = "tcs.allocation.allocation-validated.v1";
    public static final String ALLOCATION_REJECTED = "tcs.allocation.allocation-rejected.v1";
    public static final String EXECUTIONS_ALLOCATED = "tcs.allocation.executions-allocated.v1";
    public static final String EXECUTIONS_RELEASED = "tcs.allocation.executions-released.v1";
    public static final String EXCEPTION_REPORTED = "tcs.allocation.exception-reported.v1";
    public static final String SETTLEMENT_REQUESTED = "tcs.allocation.settlement-requested.v1";

    /**
     * Non-blocking retry topic suffix (spring-kafka appends the attempt index:
     * {@code -retry-0}, {@code -retry-1}, {@code -retry-2}).
     */
    public static final String RETRY_SUFFIX = "-retry";

    /** Dead-letter topic suffix for messages whose transient-failure retries are exhausted. */
    public static final String DLT_SUFFIX = "-dlt";

    /** All outbound domain-event topics, in catalog order. */
    public static final List<String> OUTBOUND_TOPICS = List.of(
            ALLOCATION_RECEIVED,
            ALLOCATION_VALIDATED,
            ALLOCATION_REJECTED,
            EXECUTIONS_ALLOCATED,
            EXECUTIONS_RELEASED,
            EXCEPTION_REPORTED,
            SETTLEMENT_REQUESTED);

    private EventTopics() {
    }
}
