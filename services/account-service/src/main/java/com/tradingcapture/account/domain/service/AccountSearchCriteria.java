package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;

/** Filter inputs for the account search endpoint (ownerSubject honored only for admins). */
public record AccountSearchCriteria(String ownerSubject,
                                    AccountStatus status,
                                    AccountType accountType,
                                    String currency) {
}
