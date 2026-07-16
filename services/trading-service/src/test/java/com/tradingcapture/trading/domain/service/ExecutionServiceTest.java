package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.MiscFeeBasis;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.repository.OrderRepository;
import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import com.tradingcapture.trading.observability.TradingMetrics;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private ExecutionRepository executions;

    private ExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionService(orders, executions,
                new TradingMetrics(new SimpleMeterRegistry()),
                Clock.fixed(TradingFixtures.FIXED_NOW, ZoneOffset.UTC));
        lenient().when(executions.save(any(Execution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ExecutionCommands.CreateTrade tradeCommand(String orderId, String qty, String px) {
        return new ExecutionCommands.CreateTrade(orderId, null, new BigDecimal(qty),
                new BigDecimal(px), null, null, "unit test", null);
    }

    // ---- creation ----

    @Test
    void createDerivesAggregatesServerSideAndUpdatesOrderTransactionally() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of());

        Execution execution = service.createTrade(TradingFixtures.admin(), tradeCommand("ORD-1", "60", "10.00"));

        assertThat(execution.getExecType()).isEqualTo(ExecutionType.TRADE);
        assertThat(execution.getInternalId()).startsWith("exec_");
        assertThat(execution.getExecId()).startsWith("EXEC-");
        assertThat(execution.getExecRefId()).isNull();
        assertThat(execution.getRootExecutionId()).isEqualTo(execution.getInternalId());
        assertThat(execution.getCumQty()).isEqualByComparingTo("60");
        assertThat(execution.getLeavesQty()).isEqualByComparingTo("40");
        assertThat(execution.getAvgPx()).isEqualByComparingTo("10.00");
        assertThat(execution.getOrdStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        // snapshots come from the order, not the request
        assertThat(execution.getSide()).isEqualTo(order.getSide());
        assertThat(execution.getCurrency()).isEqualTo("USD");
        assertThat(execution.getSettleDate()).isEqualTo(order.getSettleDate());
        // defaults derived from the fixed clock
        assertThat(execution.getTransactTime()).isEqualTo(TradingFixtures.FIXED_NOW);
        assertThat(execution.getTradeDate()).isEqualTo(LocalDate.parse("2026-07-14"));
        // the order aggregate was updated in the same unit of work
        assertThat(order.getCumQty()).isEqualByComparingTo("60");
        assertThat(order.getLeavesQty()).isEqualByComparingTo("40");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    void createHonoursExplicitTradeDateAndTransactTime() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of());
        Instant explicitTime = Instant.parse("2026-07-10T15:30:00Z");

        Execution execution = service.createTrade(TradingFixtures.admin(),
                new ExecutionCommands.CreateTrade("ORD-1", "EXEC-CLIENT-1", new BigDecimal("100"),
                        new BigDecimal("189.20"), LocalDate.parse("2026-07-10"), explicitTime, null,
                        List.of(new ExecutionCommands.MiscFee("REGULATORY", new BigDecimal("0.04"), "USD",
                                MiscFeeBasis.ABSOLUTE))));

        assertThat(execution.getExecId()).isEqualTo("EXEC-CLIENT-1");
        assertThat(execution.getTradeDate()).isEqualTo(LocalDate.parse("2026-07-10"));
        assertThat(execution.getTransactTime()).isEqualTo(explicitTime);
        assertThat(execution.getMiscFees()).hasSize(1);
        assertThat(execution.getMiscFees().get(0).getAmount()).isEqualByComparingTo("0.04");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void createRejectsUnknownOrder() {
        when(orders.findById("ORD-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTrade(TradingFixtures.admin(), tradeCommand("ORD-MISSING", "10", "1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsFilledOrder() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        order.applyFillState(new BigDecimal("100"), TradingFixtures.FIXED_NOW);
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createTrade(TradingFixtures.admin(), tradeCommand("ORD-1", "1", "1")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_FILLED));
        verify(executions, never()).save(any());
    }

    @Test
    void createRejectsOverfill() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        order.applyFillState(new BigDecimal("70"), TradingFixtures.FIXED_NOW);
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createTrade(TradingFixtures.admin(), tradeCommand("ORD-1", "31", "10")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_OVERFILL));
        assertThat(order.getCumQty()).isEqualByComparingTo("70");
    }

    @Test
    void createRejectsDuplicateExecId() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.existsByExecId("EXEC-DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.createTrade(TradingFixtures.admin(),
                new ExecutionCommands.CreateTrade("ORD-1", "EXEC-DUP", BigDecimal.ONE, BigDecimal.ONE,
                        null, null, null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DUPLICATE_EXEC_ID));
    }

    @Test
    void createOnUnownedOrderIsMaskedAsNotFoundForInvestors() {
        Order order = TradingFixtures.order("ORD-1", "investor2", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createTrade(TradingFixtures.investor("investor1"),
                tradeCommand("ORD-1", "10", "1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- correction ----

    @Test
    void correctAppendsLinkedRecordAndRecomputesAggregates() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        order.applyFillState(new BigDecimal("60"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of(target));

        Execution correction = service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, new BigDecimal("50"), new BigDecimal("10.50"), "fix qty", null));

        assertThat(correction.getExecType()).isEqualTo(ExecutionType.TRADE_CORRECT);
        assertThat(correction.getExecRefId()).isEqualTo("exec_t1");
        assertThat(correction.getRootExecutionId()).isEqualTo("exec_t1");
        assertThat(correction.getCumQty()).isEqualByComparingTo("50");
        assertThat(correction.getLeavesQty()).isEqualByComparingTo("50");
        assertThat(correction.getOrdStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getCumQty()).isEqualByComparingTo("50");
        // the historical target row is untouched
        assertThat(target.getLastQty()).isEqualByComparingTo("60");
        assertThat(target.getExecType()).isEqualTo(ExecutionType.TRADE);
    }

    @Test
    void correctResolvesRootThroughMultiStepChains() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution root = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        Execution firstCorrection = TradingFixtures.amendment(order, "exec_c1",
                ExecutionType.TRADE_CORRECT, root, "50", "10.50");
        order.applyFillState(new BigDecimal("50"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_c1")).thenReturn(Optional.of(firstCorrection));
        when(executions.existsByExecRefId("exec_c1")).thenReturn(false);
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1"))
                .thenReturn(List.of(root, firstCorrection));

        Execution second = service.correct(TradingFixtures.admin(), "exec_c1",
                new ExecutionCommands.Correct(null, new BigDecimal("55"), new BigDecimal("11.00"), null, null));

        assertThat(second.getExecRefId()).isEqualTo("exec_c1");
        assertThat(second.getRootExecutionId()).isEqualTo("exec_t1"); // root, not the direct target
        assertThat(order.getCumQty()).isEqualByComparingTo("55");
    }

    @Test
    void correctRejectsSupersededTarget() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(true);

        assertThatThrownBy(() -> service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, BigDecimal.ONE, BigDecimal.ONE, null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_TARGET_SUPERSEDED));
    }

    @Test
    void correctRejectsCancellationTarget() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        Execution cancellation = TradingFixtures.amendment(order, "exec_x1",
                ExecutionType.TRADE_CANCEL, trade, "0", null);
        when(executions.findById("exec_x1")).thenReturn(Optional.of(cancellation));

        assertThatThrownBy(() -> service.correct(TradingFixtures.admin(), "exec_x1",
                new ExecutionCommands.Correct(null, BigDecimal.ONE, BigDecimal.ONE, null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_TARGET_CANCELLED));
    }

    @Test
    void correctRejectsAllocatedTarget() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        target.allocate("ALLOC-1", TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);

        assertThatThrownBy(() -> service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, BigDecimal.ONE, BigDecimal.ONE, null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_ALLOCATED));
    }

    @Test
    void correctRejectsOverfillingCorrection() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        order.applyFillState(new BigDecimal("60"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);

        assertThatThrownBy(() -> service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, new BigDecimal("101"), BigDecimal.ONE, null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_OVERFILL));
    }

    @Test
    void correctionInheritsTargetFeesWhenFeesOmitted() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        target.addMiscFee(new com.tradingcapture.trading.domain.model.ExecutionMiscFee(target,
                "REGULATORY", new BigDecimal("0.0400"), "USD", MiscFeeBasis.ABSOLUTE,
                TradingFixtures.FIXED_NOW));
        order.applyFillState(new BigDecimal("60"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of(target));

        Execution inherited = service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, new BigDecimal("60"), new BigDecimal("11"), null, null));
        assertThat(inherited.getMiscFees()).hasSize(1);
        assertThat(inherited.getMiscFees().get(0).getFeeType()).isEqualTo("REGULATORY");
    }

    @Test
    void correctionWithEmptyFeeListCarriesNoFees() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        target.addMiscFee(new com.tradingcapture.trading.domain.model.ExecutionMiscFee(target,
                "REGULATORY", new BigDecimal("0.0400"), "USD", MiscFeeBasis.ABSOLUTE,
                TradingFixtures.FIXED_NOW));
        order.applyFillState(new BigDecimal("60"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of(target));

        Execution cleared = service.correct(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Correct(null, new BigDecimal("60"), new BigDecimal("11"), null, List.of()));
        assertThat(cleared.getMiscFees()).isEmpty();
    }

    // ---- cancellation ----

    @Test
    void cancelVoidsTheChainAndCanReturnOrderToNew() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        order.applyFillState(new BigDecimal("60"), TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);
        when(executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc("ORD-1")).thenReturn(List.of(target));

        Execution cancellation = service.cancel(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Cancel(null, "bust"));

        assertThat(cancellation.getExecType()).isEqualTo(ExecutionType.TRADE_CANCEL);
        assertThat(cancellation.getExecRefId()).isEqualTo("exec_t1");
        assertThat(cancellation.getRootExecutionId()).isEqualTo("exec_t1");
        assertThat(cancellation.getLastQty()).isEqualByComparingTo("0");
        assertThat(cancellation.getLastPx()).isNull();
        assertThat(cancellation.getCumQty()).isEqualByComparingTo("0");
        assertThat(cancellation.getOrdStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getLeavesQty()).isEqualByComparingTo("100");
    }

    @Test
    void cancelRejectsAlreadyCancelledChainTip() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        Execution cancellation = TradingFixtures.amendment(order, "exec_x1",
                ExecutionType.TRADE_CANCEL, trade, "0", null);
        when(executions.findById("exec_x1")).thenReturn(Optional.of(cancellation));

        assertThatThrownBy(() -> service.cancel(TradingFixtures.admin(), "exec_x1",
                new ExecutionCommands.Cancel(null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_TARGET_CANCELLED));
    }

    @Test
    void cancelRejectsSupersededTarget() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Cancel(null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_TARGET_SUPERSEDED));
    }

    @Test
    void cancelRejectsAllocatedTarget() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        Execution target = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        target.allocate("ALLOC-1", TradingFixtures.FIXED_NOW);
        when(executions.findById("exec_t1")).thenReturn(Optional.of(target));
        when(executions.existsByExecRefId("exec_t1")).thenReturn(false);

        assertThatThrownBy(() -> service.cancel(TradingFixtures.admin(), "exec_t1",
                new ExecutionCommands.Cancel(null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXECUTION_ALLOCATED));
    }

    @Test
    void amendmentOfMissingTargetIsNotFound() {
        when(executions.findById("exec_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(TradingFixtures.admin(), "exec_missing",
                new ExecutionCommands.Cancel(null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
