package com.tradingcapture.allocation.ingest;

/** Deterministic result of processing one inbound record (drives metrics and tests). */
public enum IngestionOutcome {

    /** Allocation persisted RECEIVED; AllocationReceived recorded to the outbox. */
    ACCEPTED,
    /** Field validation failed; INVALID state persisted with all errors; rejection events recorded. */
    INVALID,
    /** Unparseable FIX; PARSE_FAILED persisted; ExceptionReported recorded. */
    PARSE_FAILED,
    /** Unsupported MsgType; PARSE_FAILED persisted; ExceptionReported recorded. */
    UNSUPPORTED_TYPE,
    /** Business duplicate (same AllocID + AllocTransType already processed); no events. */
    DUPLICATE_BUSINESS,
    /** The raw message was already in a terminal state (broker redelivery); nothing done. */
    ALREADY_PROCESSED
}
