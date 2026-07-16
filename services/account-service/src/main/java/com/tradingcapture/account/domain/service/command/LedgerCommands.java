package com.tradingcapture.account.domain.service.command;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransactionType;

import java.math.BigDecimal;

/** Service-layer commands for appending ledger rows. */
public final class LedgerCommands {

    private LedgerCommands() {
    }

    public record PostCash(CashDirection direction,
                           CashTransactionType transactionType,
                           BigDecimal amount,
                           String currency,
                           String externalRef,
                           String idempotencyKey,
                           String description) {
    }

    public record PostSecurity(SecurityDirection direction,
                               SecurityTransactionType transactionType,
                               String instrumentId,
                               BigDecimal quantity,
                               String externalRef,
                               String idempotencyKey,
                               String description) {
    }
}
