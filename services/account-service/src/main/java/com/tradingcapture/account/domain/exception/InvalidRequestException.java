package com.tradingcapture.account.domain.exception;

/** A request is structurally valid but semantically wrong (HTTP 400). */
public class InvalidRequestException extends AccountDomainException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
