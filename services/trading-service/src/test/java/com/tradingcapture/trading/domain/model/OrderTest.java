package com.tradingcapture.trading.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant T0 = Instant.parse("2026-07-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-01T10:00:00Z");

    private Order newOrder(String quantity) {
        return new Order("ORD-1", "cl-1", "investor1", "ACC-1", "AAPL",
                Side.BUY, OrderType.LIMIT, new BigDecimal("189.25000000"), PriceType.PER_UNIT,
                "USD", new BigDecimal(quantity), SettleType.T_PLUS_2,
                LocalDate.parse("2026-07-03"), T0);
    }

    @Test
    void newOrderStartsNewWithZeroCumAndFullLeaves() {
        Order order = newOrder("100.0000");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getCumQty()).isEqualByComparingTo("0");
        assertThat(order.getLeavesQty()).isEqualByComparingTo("100");
        assertThat(order.getOwnerSubject()).isEqualTo("investor1");
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void fillStateDerivesPartiallyFilled() {
        Order order = newOrder("100.0000");

        order.applyFillState(new BigDecimal("40.0000"), T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getCumQty()).isEqualByComparingTo("40");
        assertThat(order.getLeavesQty()).isEqualByComparingTo("60");
        assertThat(order.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void fillStateDerivesFilledAtExactQuantity() {
        Order order = newOrder("100.0000");

        order.applyFillState(new BigDecimal("100.0000"), T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getLeavesQty()).isEqualByComparingTo("0");
    }

    @Test
    void fillStateReturnsToNewWhenEverythingIsCancelled() {
        Order order = newOrder("100.0000");
        order.applyFillState(new BigDecimal("40.0000"), T1);

        order.applyFillState(BigDecimal.ZERO, T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getLeavesQty()).isEqualByComparingTo("100");
    }

    @Test
    void fillStateRejectsCumBeyondQuantity() {
        Order order = newOrder("100.0000");

        assertThatThrownBy(() -> order.applyFillState(new BigDecimal("100.0001"), T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fillStateRejectsNegativeCum() {
        Order order = newOrder("100.0000");

        assertThatThrownBy(() -> order.applyFillState(new BigDecimal("-0.0001"), T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateRecomputesLeavesFromNewQuantity() {
        Order order = newOrder("100.0000");

        order.applyUpdate("cl-2", "ACC-2", "MSFT", Side.SELL, OrderType.MARKET,
                new BigDecimal("415.50000000"), PriceType.PER_UNIT, "EUR",
                new BigDecimal("250.0000"), SettleType.REGULAR, LocalDate.parse("2026-07-06"), T1);

        assertThat(order.getQuantity()).isEqualByComparingTo("250");
        assertThat(order.getLeavesQty()).isEqualByComparingTo("250");
        assertThat(order.getInstrumentId()).isEqualTo("MSFT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getUpdatedAt()).isEqualTo(T1);
    }
}
