package com.tradingcapture.account.domain.exception;

/** A state-dependent domain rule rejected the request (HTTP 409). */
public class BusinessRuleException extends AccountDomainException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
