package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.service.validation.AccountValidationService;

import java.util.List;

/** Per-item validation verdicts, in the same order as the request items. */
public record AccountBatchValidationResponse(List<Result> results) {

    public record Result(String accountId,
                         boolean found,
                         AccountStatus status,
                         boolean active,
                         String ownerSubject,
                         Boolean sufficientCash,
                         Boolean sufficientSecurities,
                         List<String> reasons) {
    }

    public static AccountBatchValidationResponse from(List<AccountValidationService.Outcome> outcomes) {
        return new AccountBatchValidationResponse(outcomes.stream()
                .map(o -> new Result(o.accountId(), o.found(), o.status(), o.active(), o.ownerSubject(),
                        o.sufficientCash(), o.sufficientSecurities(), o.reasons()))
                .toList());
    }
}
