package com.tradingcapture.trading.domain.port;

import com.tradingcapture.trading.domain.exception.InvalidReferenceException;

/**
 * Boundary to account master data, owned by account-service (Phase 4). Order capture checks
 * that the account is acceptable for trading; balance logic never lives here.
 */
public interface AccountReferencePort {

    /** @throws InvalidReferenceException when the account is not acceptable for trading */
    void validateAccountForTrading(String accountId);
}
