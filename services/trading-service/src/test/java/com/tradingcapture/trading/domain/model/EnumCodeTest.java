package com.tradingcapture.trading.domain.model;

import com.tradingcapture.trading.domain.model.converter.AllocationStateConverter;
import com.tradingcapture.trading.domain.model.converter.ExecutionTypeConverter;
import com.tradingcapture.trading.domain.model.converter.MiscFeeBasisConverter;
import com.tradingcapture.trading.domain.model.converter.OrderStatusConverter;
import com.tradingcapture.trading.domain.model.converter.OrderTypeConverter;
import com.tradingcapture.trading.domain.model.converter.PriceTypeConverter;
import com.tradingcapture.trading.domain.model.converter.SettleTypeConverter;
import com.tradingcapture.trading.domain.model.converter.SideConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the persisted FIX code mappings (ported from the reference domain) and the
 * fail-loudly rule for unknown codes — silently defaulting would corrupt history.
 */
class EnumCodeTest {

    @Test
    void fixCodeMappingsMatchTheReferenceDomain() {
        assertThat(OrderStatus.NEW.code()).isEqualTo("0");
        assertThat(OrderStatus.PARTIALLY_FILLED.code()).isEqualTo("1");
        assertThat(OrderStatus.FILLED.code()).isEqualTo("2");
        assertThat(Side.BUY.code()).isEqualTo("1");
        assertThat(Side.SELL.code()).isEqualTo("2");
        assertThat(OrderType.MARKET.code()).isEqualTo("1");
        assertThat(OrderType.LIMIT.code()).isEqualTo("2");
        assertThat(PriceType.PERCENTAGE.code()).isEqualTo("0");
        assertThat(PriceType.PER_UNIT.code()).isEqualTo("1");
        assertThat(PriceType.FIXED_AMOUNT.code()).isEqualTo("2");
        assertThat(SettleType.REGULAR.code()).isEqualTo("0");
        assertThat(SettleType.T_PLUS_2.code()).isEqualTo("3");
        assertThat(ExecutionType.TRADE.code()).isEqualTo("F");
        assertThat(ExecutionType.TRADE_CORRECT.code()).isEqualTo("G");
        assertThat(ExecutionType.TRADE_CANCEL.code()).isEqualTo("H");
        assertThat(MiscFeeBasis.ABSOLUTE.code()).isEqualTo("0");
    }

    @Test
    void convertersRoundTripEveryValue() {
        var statusConverter = new OrderStatusConverter();
        for (OrderStatus value : OrderStatus.values()) {
            assertThat(statusConverter.convertToEntityAttribute(statusConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var sideConverter = new SideConverter();
        for (Side value : Side.values()) {
            assertThat(sideConverter.convertToEntityAttribute(sideConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var orderTypeConverter = new OrderTypeConverter();
        for (OrderType value : OrderType.values()) {
            assertThat(orderTypeConverter.convertToEntityAttribute(orderTypeConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var priceTypeConverter = new PriceTypeConverter();
        for (PriceType value : PriceType.values()) {
            assertThat(priceTypeConverter.convertToEntityAttribute(priceTypeConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var settleTypeConverter = new SettleTypeConverter();
        for (SettleType value : SettleType.values()) {
            assertThat(settleTypeConverter.convertToEntityAttribute(settleTypeConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var executionTypeConverter = new ExecutionTypeConverter();
        for (ExecutionType value : ExecutionType.values()) {
            assertThat(executionTypeConverter.convertToEntityAttribute(executionTypeConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var allocationStateConverter = new AllocationStateConverter();
        for (AllocationState value : AllocationState.values()) {
            assertThat(allocationStateConverter.convertToEntityAttribute(allocationStateConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        var basisConverter = new MiscFeeBasisConverter();
        for (MiscFeeBasis value : MiscFeeBasis.values()) {
            assertThat(basisConverter.convertToEntityAttribute(basisConverter.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
    }

    @Test
    void unknownCodesFailLoudly() {
        assertThatThrownBy(() -> OrderStatus.fromCode("9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Side.fromCode("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExecutionType.fromCode("Z")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderStatusConverter().convertToEntityAttribute("9"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertersPassNullsThrough() {
        assertThat(new OrderStatusConverter().convertToDatabaseColumn(null)).isNull();
        assertThat(new OrderStatusConverter().convertToEntityAttribute(null)).isNull();
    }

    @Test
    void allocationStateAllowsOnlyCrossTransitions() {
        assertThat(AllocationState.UNALLOCATED.canTransitionTo(AllocationState.ALLOCATED)).isTrue();
        assertThat(AllocationState.ALLOCATED.canTransitionTo(AllocationState.UNALLOCATED)).isTrue();
        assertThat(AllocationState.ALLOCATED.canTransitionTo(AllocationState.ALLOCATED)).isFalse();
        assertThat(AllocationState.UNALLOCATED.canTransitionTo(AllocationState.UNALLOCATED)).isFalse();
    }
}
