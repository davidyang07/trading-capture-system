package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.InvalidRequestException;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.Side;

import java.time.LocalDate;

/**
 * Order search filters; every component is optional. {@code ownerSubject} is honored only
 * for privileged callers — the service forces non-privileged callers onto their own subject.
 */
public record OrderSearchCriteria(OrderStatus status,
                                  Side side,
                                  String instrumentId,
                                  String accountId,
                                  String ownerSubject,
                                  LocalDate settleDateFrom,
                                  LocalDate settleDateTo) {

    public OrderSearchCriteria {
        if (settleDateFrom != null && settleDateTo != null && settleDateFrom.isAfter(settleDateTo)) {
            throw new InvalidRequestException("settleDateFrom must not be after settleDateTo");
        }
    }
}
