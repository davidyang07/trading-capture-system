package com.tradingcapture.account.api.mapper;

import com.tradingcapture.account.api.dto.AccountResponse;
import com.tradingcapture.account.domain.model.Account;
import org.springframework.stereotype.Component;

/** Hand-written entity-to-DTO mapping (ADR-0014: no MapStruct, entities never serialized). */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getAccountId(),
                account.getOwnerSubject(),
                account.getAccountName(),
                account.getAccountType(),
                account.getStatus(),
                account.getBaseCurrency(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getVersion());
    }
}
