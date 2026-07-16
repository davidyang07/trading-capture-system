package com.tradingcapture.account.domain.service.command;

import com.tradingcapture.account.domain.model.AccountType;

/** Service-layer commands for account lifecycle, decoupled from the API DTOs. */
public final class AccountCommands {

    private AccountCommands() {
    }

    /**
     * @param accountId     caller-supplied id, or {@code null} to generate one
     * @param ownerSubject  admin-nominated owner, or {@code null} to use the caller's subject
     */
    public record CreateAccount(String accountId,
                                String ownerSubject,
                                String accountName,
                                AccountType accountType,
                                String baseCurrency) {
    }

    public record UpdateAccount(String accountName) {
    }
}
