package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.service.AccountSearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Account search query parameters. Investors are always restricted to their own accounts
 * regardless of these filters; {@code ownerSubject} is honored for administrators only.
 * Sorting always appends accountId ASC as tiebreaker so pages are deterministic.
 */
public record AccountSearchRequest(
        AccountStatus status,
        AccountType accountType,
        String currency,
        @Schema(description = "Administrator-only filter; ignored for investors")
        String ownerSubject,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        AccountSortField sortBy,
        SortDirection sortDir) {

    public AccountSearchCriteria toCriteria() {
        return new AccountSearchCriteria(ownerSubject, status, accountType, currency);
    }

    public Pageable toPageable() {
        AccountSortField field = sortBy != null ? sortBy : AccountSortField.CREATED_AT;
        SortDirection direction = sortDir != null ? sortDir : SortDirection.DESC;
        Sort sort = Sort.by(direction.direction(), field.property())
                .and(Sort.by(Sort.Direction.ASC, "accountId"));
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20, sort);
    }
}
