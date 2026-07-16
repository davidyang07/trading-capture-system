package com.tradingcapture.trading.testsupport;

import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.service.AccessContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** In-memory fixtures for unit tests (no persistence involved). */
public final class TradingFixtures {

    public static final Instant FIXED_NOW = Instant.parse("2026-07-14T12:00:00Z");

    private TradingFixtures() {
    }

    public static AccessContext admin() {
        return new AccessContext("tcs-admin", true, false);
    }

    public static AccessContext investor(String subject) {
        return new AccessContext(subject, false, false);
    }

    public static AccessContext machine() {
        return new AccessContext("service-account-allocation-engine", false, true);
    }

    public static Order order(String orderId, String owner, String quantity) {
        return new Order(orderId, "cl-" + orderId, owner, "ACC-1", "AAPL",
                Side.BUY, OrderType.LIMIT, new BigDecimal("189.25000000"), PriceType.PER_UNIT,
                "USD", new BigDecimal(quantity), SettleType.T_PLUS_2,
                LocalDate.parse("2026-07-03"), FIXED_NOW.minusSeconds(3600));
    }

    public static Execution trade(Order order, String internalId, String lastQty, String lastPx) {
        BigDecimal qty = new BigDecimal(lastQty);
        return Execution.builder()
                .internalId(internalId)
                .execId("EXT-" + internalId)
                .order(order)
                .execType(ExecutionType.TRADE)
                .rootExecutionId(internalId)
                .ordStatus(OrderStatus.PARTIALLY_FILLED)
                .side(order.getSide())
                .lastQty(qty)
                .lastPx(new BigDecimal(lastPx))
                .cumQty(qty)
                .leavesQty(order.getQuantity().subtract(qty))
                .avgPx(new BigDecimal(lastPx))
                .currency(order.getCurrency())
                .tradeDate(LocalDate.parse("2026-07-01"))
                .transactTime(FIXED_NOW.minusSeconds(1800))
                .settleType(order.getSettleType())
                .settleDate(order.getSettleDate())
                .createdAt(FIXED_NOW.minusSeconds(1800))
                .build();
    }

    public static Execution amendment(Order order, String internalId, ExecutionType type,
                                      Execution target, String lastQty, String lastPx) {
        return Execution.builder()
                .internalId(internalId)
                .execId("EXT-" + internalId)
                .order(order)
                .execType(type)
                .execRefId(target.getInternalId())
                .rootExecutionId(target.getRootExecutionId())
                .ordStatus(OrderStatus.PARTIALLY_FILLED)
                .side(order.getSide())
                .lastQty(new BigDecimal(lastQty))
                .lastPx(lastPx == null ? null : new BigDecimal(lastPx))
                .cumQty(new BigDecimal(lastQty))
                .leavesQty(order.getQuantity().subtract(new BigDecimal(lastQty)))
                .avgPx(lastPx == null ? null : new BigDecimal(lastPx))
                .currency(order.getCurrency())
                .tradeDate(LocalDate.parse("2026-07-01"))
                .transactTime(FIXED_NOW.minusSeconds(900))
                .settleType(order.getSettleType())
                .settleDate(order.getSettleDate())
                .createdAt(FIXED_NOW.minusSeconds(900))
                .build();
    }
}
