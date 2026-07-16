package com.tradingcapture.enrichment.domain.exception;

/** Base of all deliberate domain failures; the API layer maps subtypes to HTTP statuses. */
public abstract class EnrichmentDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected EnrichmentDomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
