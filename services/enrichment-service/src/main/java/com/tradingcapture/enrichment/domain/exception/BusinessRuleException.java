package com.tradingcapture.enrichment.domain.exception;

/** A state-dependent domain rule rejected the request — duplicates, ambiguity (HTTP 409). */
public class BusinessRuleException extends EnrichmentDomainException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
