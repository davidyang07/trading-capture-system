package com.tradingcapture.trading.domain.service.command;

import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Typed commands the order service accepts. The API layer maps validated request DTOs onto
 * these; the domain never sees raw web input. Owner identity is deliberately absent — it
 * comes only from the {@code AccessContext}.
 */
public final class OrderCommands {

    private OrderCommands() {
    }

    /** @param orderId optional client-supplied business ID; generated when null */
    public record CreateOrder(String orderId,
                              String clOrdId,
                              String accountId,
                              String instrumentId,
                              Side side,
                              OrderType orderType,
                              BigDecimal price,
                              PriceType priceType,
                              String currency,
                              BigDecimal quantity,
                              SettleType settleType,
                              LocalDate settleDate) {
    }

    /** Full replacement of the client-editable fields (PUT semantics); NEW orders only. */
    public record UpdateOrder(String clOrdId,
                              String accountId,
                              String instrumentId,
                              Side side,
                              OrderType orderType,
                              BigDecimal price,
                              PriceType priceType,
                              String currency,
                              BigDecimal quantity,
                              SettleType settleType,
                              LocalDate settleDate) {
    }
}
