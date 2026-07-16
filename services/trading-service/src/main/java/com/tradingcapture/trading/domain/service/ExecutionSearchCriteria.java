package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.InvalidRequestException;
import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Side;

import java.time.Instant;
import java.time.LocalDate;

/** Execution search filters; every component is optional. */
public record ExecutionSearchCriteria(String orderId,
                                      String execId,
                                      ExecutionType execType,
                                      Side side,
                                      AllocationState allocationState,
                                      LocalDate tradeDateFrom,
                                      LocalDate tradeDateTo,
                                      Instant transactTimeFrom,
                                      Instant transactTimeTo) {

    public ExecutionSearchCriteria {
        if (tradeDateFrom != null && tradeDateTo != null && tradeDateFrom.isAfter(tradeDateTo)) {
            throw new InvalidRequestException("tradeDateFrom must not be after tradeDateTo");
        }
        if (transactTimeFrom != null && transactTimeTo != null && transactTimeFrom.isAfter(transactTimeTo)) {
            throw new InvalidRequestException("transactTimeFrom must not be after transactTimeTo");
        }
    }
}
