package com.tradingcapture.trading.domain.exception;

/** A referenced identifier (instrument, account) failed reference validation (HTTP 400). */
public class InvalidReferenceException extends TradingDomainException {

    public InvalidReferenceException(String message) {
        super(ErrorCode.INVALID_REFERENCE, message);
    }
}
