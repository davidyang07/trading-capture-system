package com.tradingcapture.allocation.ingest;

/**
 * The single retryable signal of the ingestion pipeline: thrown only for failures that a
 * later redelivery can plausibly resolve (database connectivity, unexpected infrastructure
 * errors). {@code @RetryableTopic} retries exactly this type; every permanent failure
 * (malformed FIX, unsupported type, field validation) is handled to a terminal state and
 * never thrown. Bounded retries end on the dead-letter topic.
 */
public class TransientIngestionException extends RuntimeException {

    public TransientIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
