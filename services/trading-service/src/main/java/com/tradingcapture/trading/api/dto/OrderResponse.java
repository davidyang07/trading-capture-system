package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Order representation; cumQty/leavesQty/status are the server-derived aggregate state. */
public record OrderResponse(String orderId,
                            String clOrdId,
                            String ownerSubject,
                            String accountId,
                            String instrumentId,
                            OrderStatus status,
                            Side side,
                            OrderType orderType,
                            BigDecimal price,
                            PriceType priceType,
                            String currency,
                            BigDecimal quantity,
                            BigDecimal cumQty,
                            BigDecimal leavesQty,
                            SettleType settleType,
                            LocalDate settleDate,
                            Instant createdAt,
                            Instant updatedAt,
                            Long version) {
}
