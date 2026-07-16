package com.tradingcapture.trading.domain.exception;

/** A state-dependent domain rule rejected the request (HTTP 409). */
public class BusinessRuleException extends TradingDomainException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
