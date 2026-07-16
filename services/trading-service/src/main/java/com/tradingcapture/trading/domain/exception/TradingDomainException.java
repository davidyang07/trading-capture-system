package com.tradingcapture.trading.domain.exception;

/** Base of all deliberate domain failures; the API layer maps subtypes to HTTP statuses. */
public abstract class TradingDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected TradingDomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
