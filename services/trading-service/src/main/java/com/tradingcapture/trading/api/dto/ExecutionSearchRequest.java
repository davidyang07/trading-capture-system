package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.service.ExecutionSearchCriteria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Execution search query parameters. Investors see only executions of their own orders.
 * Sorting always appends internalId ASC as tiebreaker so pages are deterministic.
 */
public record ExecutionSearchRequest(
        String orderId,
        String execId,
        ExecutionType execType,
        Side side,
        AllocationState allocationState,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateTo,
        Instant transactTimeFrom,
        Instant transactTimeTo,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        ExecutionSortField sortBy,
        SortDirection sortDir) {

    public ExecutionSearchCriteria toCriteria() {
        return new ExecutionSearchCriteria(orderId, execId, execType, side, allocationState,
                tradeDateFrom, tradeDateTo, transactTimeFrom, transactTimeTo);
    }

    public Pageable toPageable() {
        ExecutionSortField field = sortBy != null ? sortBy : ExecutionSortField.TRANSACT_TIME;
        SortDirection direction = sortDir != null ? sortDir : SortDirection.DESC;
        Sort sort = Sort.by(direction.direction(), field.property())
                .and(Sort.by(Sort.Direction.ASC, "internalId"));
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20, sort);
    }
}
