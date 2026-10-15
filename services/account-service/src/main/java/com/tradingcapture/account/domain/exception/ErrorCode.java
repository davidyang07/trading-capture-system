package com.tradingcapture.account.domain.exception;

/**
 * Stable machine-readable error codes carried in every problem response
 * (the {@code errorCode} extension of the RFC 7807 body).
 */
public enum ErrorCode {
    ACCOUNT_NOT_FOUND,
    TRANSACTION_NOT_FOUND,
    DUPLICATE_ACCOUNT_ID,
    INVALID_STATUS_TRANSITION,
    ACCOUNT_NOT_ACTIVE,
    ACCOUNT_NOT_EMPTY,
    INSUFFICIENT_FUNDS,
    INSUFFICIENT_SECURITIES,
    IDEMPOTENCY_KEY_REUSED,
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    CONCURRENT_MODIFICATION,
    CONSTRAINT_VIOLATION,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    INTERNAL_ERROR
}
