package com.tradingcapture.enrichment.domain.exception;

/** A referenced exchange or currency does not exist (HTTP 400). */
public class InvalidReferenceException extends EnrichmentDomainException {

    public InvalidReferenceException(String message) {
        super(ErrorCode.INVALID_REFERENCE, message);
    }
}
