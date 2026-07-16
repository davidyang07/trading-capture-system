package com.tradingcapture.trading.domain.exception;

/**
 * Resource does not exist — or exists but is not visible to the caller. Ownership denials
 * are deliberately reported through this same exception (HTTP 404) so the API never
 * confirms the existence of another investor's resources.
 */
public class ResourceNotFoundException extends TradingDomainException {

    private ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException order(String orderId) {
        return new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
    }

    public static ResourceNotFoundException execution(String internalId) {
        return new ResourceNotFoundException(ErrorCode.EXECUTION_NOT_FOUND, "Execution not found: " + internalId);
    }
}
