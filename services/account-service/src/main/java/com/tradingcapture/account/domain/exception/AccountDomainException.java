package com.tradingcapture.account.domain.exception;

/** Base of all deliberate domain failures; the API layer maps subtypes to HTTP statuses. */
public abstract class AccountDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected AccountDomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
