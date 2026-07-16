package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;

import java.time.Instant;

/** Account projection returned by the API (entities are never serialized directly). */
public record AccountResponse(String accountId,
                              String ownerSubject,
                              String accountName,
                              AccountType accountType,
                              AccountStatus status,
                              String baseCurrency,
                              Instant createdAt,
                              Instant updatedAt,
                              Long version) {
}
