package com.tradingcapture.trading.integration;

import com.tradingcapture.trading.domain.exception.InvalidReferenceException;
import com.tradingcapture.trading.domain.port.AccountReferencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Phase 3 local implementation of the account boundary: enforces the identifier format
 * contract only. Remote account-status validation against account-service arrives in
 * Phase 4 behind the same port (documented in docs/status/known-gaps.md).
 */
@Component
public class LocalAccountReferenceAdapter implements AccountReferencePort {

    private static final Logger log = LoggerFactory.getLogger(LocalAccountReferenceAdapter.class);
    private static final Pattern ACCOUNT_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public void validateAccountForTrading(String accountId) {
        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) {
            throw new InvalidReferenceException(
                    "accountId must match " + ACCOUNT_ID.pattern());
        }
        log.debug("Account {} accepted by local format validation (remote account validation is a Phase 4 integration)",
                accountId);
    }
}
