package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Execution list/summary representation. cumQty/leavesQty/avgPx/ordStatus are the
 * server-derived aggregate snapshot as of this event.
 */
public record ExecutionResponse(String internalId,
                                String execId,
                                String orderId,
                                ExecutionType execType,
                                String execRefId,
                                String rootExecutionId,
                                OrderStatus ordStatus,
                                Side side,
                                BigDecimal lastQty,
                                BigDecimal lastPx,
                                BigDecimal cumQty,
                                BigDecimal leavesQty,
                                BigDecimal avgPx,
                                String currency,
                                LocalDate tradeDate,
                                Instant transactTime,
                                SettleType settleType,
                                LocalDate settleDate,
                                String text,
                                AllocationState allocationState,
                                String allocationId,
                                Instant createdAt,
                                Long version) {
}
