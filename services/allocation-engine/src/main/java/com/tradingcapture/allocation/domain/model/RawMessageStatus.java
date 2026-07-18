package com.tradingcapture.allocation.domain.model;

/**
 * Deterministic processing state of an inbound Kafka record (see V1 migration header).
 * {@code RECEIVED} is the only non-terminal state: a redelivery that finds it resumes
 * processing; every other state means the record's outcome is already durable.
 */
public enum RawMessageStatus implements CodeEnum {

    /** Persisted verbatim; processing outcome not yet durable. */
    RECEIVED,
    /** Parsed and field-valid; an allocation row exists in state RECEIVED. */
    PROCESSED,
    /** Parsed but field validation failed; an allocation row exists in state INVALID. */
    INVALID,
    /** Unparseable or unsupported message type; no allocation row exists. */
    PARSE_FAILED,
    /** Business duplicate of an already-processed allocation (same AllocID + AllocTransType). */
    DUPLICATE,
    /** Transient-failure retries exhausted (set from the dead-letter handler). */
    FAILED;

    @Override
    public String code() {
        return name();
    }

    public boolean isTerminal() {
        return this != RECEIVED;
    }
}
