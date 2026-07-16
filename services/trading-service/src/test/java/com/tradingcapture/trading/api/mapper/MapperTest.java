package com.tradingcapture.trading.api.mapper;

import com.tradingcapture.trading.api.dto.ExecutionDetailResponse;
import com.tradingcapture.trading.api.dto.ExecutionResponse;
import com.tradingcapture.trading.api.dto.OrderDetailResponse;
import com.tradingcapture.trading.api.dto.OrderResponse;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionMiscFee;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.MiscFeeBasis;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.service.ExecutionQueryService.ExecutionWithChain;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private final ExecutionMapper executionMapper = new ExecutionMapper();
    private final OrderMapper orderMapper = new OrderMapper(executionMapper);

    @Test
    void orderMapsFieldForField() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        order.applyFillState(new BigDecimal("40.0000"), TradingFixtures.FIXED_NOW);

        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response.orderId()).isEqualTo("ORD-1");
        assertThat(response.clOrdId()).isEqualTo("cl-ORD-1");
        assertThat(response.ownerSubject()).isEqualTo("investor1");
        assertThat(response.accountId()).isEqualTo("ACC-1");
        assertThat(response.instrumentId()).isEqualTo("AAPL");
        assertThat(response.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(response.side()).isEqualTo(Side.BUY);
        assertThat(response.orderType()).isEqualTo(OrderType.LIMIT);
        assertThat(response.price()).isEqualByComparingTo("189.25");
        assertThat(response.priceType()).isEqualTo(PriceType.PER_UNIT);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.quantity()).isEqualByComparingTo("100");
        assertThat(response.cumQty()).isEqualByComparingTo("40");
        assertThat(response.leavesQty()).isEqualByComparingTo("60");
        assertThat(response.settleType()).isEqualTo(SettleType.T_PLUS_2);
        assertThat(response.settleDate()).isEqualTo(order.getSettleDate());
        assertThat(response.createdAt()).isEqualTo(order.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(TradingFixtures.FIXED_NOW);
    }

    @Test
    void orderDetailIncludesExecutionHistory() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");

        OrderDetailResponse detail = orderMapper.toDetailResponse(order, List.of(trade));

        assertThat(detail.order().orderId()).isEqualTo("ORD-1");
        assertThat(detail.executions()).hasSize(1);
        assertThat(detail.executions().get(0).internalId()).isEqualTo("exec_t1");
        assertThat(detail.executions().get(0).orderId()).isEqualTo("ORD-1");
    }

    @Test
    void executionMapsFieldForField() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        trade.allocate("ALLOC-1", TradingFixtures.FIXED_NOW);

        ExecutionResponse response = executionMapper.toResponse(trade);

        assertThat(response.internalId()).isEqualTo("exec_t1");
        assertThat(response.execId()).isEqualTo("EXT-exec_t1");
        assertThat(response.orderId()).isEqualTo("ORD-1");
        assertThat(response.execType()).isEqualTo(ExecutionType.TRADE);
        assertThat(response.execRefId()).isNull();
        assertThat(response.rootExecutionId()).isEqualTo("exec_t1");
        assertThat(response.lastQty()).isEqualByComparingTo("60");
        assertThat(response.lastPx()).isEqualByComparingTo("10.00");
        assertThat(response.cumQty()).isEqualByComparingTo("60");
        assertThat(response.leavesQty()).isEqualByComparingTo("40");
        assertThat(response.allocationState().name()).isEqualTo("ALLOCATED");
        assertThat(response.allocationId()).isEqualTo("ALLOC-1");
    }

    @Test
    void executionDetailMarksTheChainTipAndActivity() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        trade.addMiscFee(new ExecutionMiscFee(trade, "REGULATORY", new BigDecimal("0.0400"),
                "USD", MiscFeeBasis.ABSOLUTE, TradingFixtures.FIXED_NOW));
        Execution correction = TradingFixtures.amendment(order, "exec_c1",
                ExecutionType.TRADE_CORRECT, trade, "50", "10.50");

        ExecutionDetailResponse detail = executionMapper.toDetailResponse(
                new ExecutionWithChain(trade, List.of(trade, correction)));

        assertThat(detail.chainRootExecutionId()).isEqualTo("exec_t1");
        assertThat(detail.chainActive()).isTrue();
        assertThat(detail.chain()).hasSize(2);
        assertThat(detail.chain().get(0).internalId()).isEqualTo("exec_t1");
        assertThat(detail.chain().get(0).tip()).isFalse();
        assertThat(detail.chain().get(1).internalId()).isEqualTo("exec_c1");
        assertThat(detail.chain().get(1).tip()).isTrue();
        assertThat(detail.miscFees()).hasSize(1);
        assertThat(detail.miscFees().get(0).feeType()).isEqualTo("REGULATORY");
    }

    @Test
    void executionDetailFlagsCancelledChainsAsInactive() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        Execution cancellation = TradingFixtures.amendment(order, "exec_x1",
                ExecutionType.TRADE_CANCEL, trade, "0", null);

        ExecutionDetailResponse detail = executionMapper.toDetailResponse(
                new ExecutionWithChain(trade, List.of(trade, cancellation)));

        assertThat(detail.chainActive()).isFalse();
        assertThat(detail.chain().get(1).tip()).isTrue();
    }
}
