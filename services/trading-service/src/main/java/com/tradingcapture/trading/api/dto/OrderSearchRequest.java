package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.service.OrderSearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Order search query parameters. Investors are always restricted to their own orders
 * regardless of these filters; {@code ownerSubject} is honored for administrators only.
 * Sorting always appends orderId ASC as tiebreaker so pages are deterministic.
 */
public record OrderSearchRequest(
        OrderStatus status,
        Side side,
        String instrumentId,
        String accountId,
        @Schema(description = "Administrator-only filter; ignored for investors")
        String ownerSubject,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settleDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settleDateTo,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        OrderSortField sortBy,
        SortDirection sortDir) {

    public OrderSearchCriteria toCriteria() {
        return new OrderSearchCriteria(status, side, instrumentId, accountId, ownerSubject,
                settleDateFrom, settleDateTo);
    }

    public Pageable toPageable() {
        OrderSortField field = sortBy != null ? sortBy : OrderSortField.CREATED_AT;
        SortDirection direction = sortDir != null ? sortDir : SortDirection.DESC;
        Sort sort = Sort.by(direction.direction(), field.property())
                .and(Sort.by(Sort.Direction.ASC, "orderId"));
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20, sort);
    }
}
