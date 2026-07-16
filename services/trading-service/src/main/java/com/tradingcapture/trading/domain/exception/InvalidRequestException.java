package com.tradingcapture.trading.domain.exception;

/** A request is structurally valid but semantically wrong, e.g. an inverted date range (HTTP 400). */
public class InvalidRequestException extends TradingDomainException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
