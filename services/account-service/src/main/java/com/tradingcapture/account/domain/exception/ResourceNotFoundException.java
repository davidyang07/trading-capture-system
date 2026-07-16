package com.tradingcapture.account.domain.exception;

/**
 * Resource does not exist — or exists but is not visible to the caller. Ownership denials
 * are deliberately reported through this same exception (HTTP 404) so the API never
 * confirms the existence of another investor's account.
 */
public class ResourceNotFoundException extends AccountDomainException {

    private ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException account(String accountId) {
        return new ResourceNotFoundException(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountId);
    }
}
