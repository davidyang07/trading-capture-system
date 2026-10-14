package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;
import com.tradingcapture.trading.observability.TradingMetrics;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationStateServiceTest {

    @Mock
    private ExecutionRepository executions;

    private AllocationStateService service;
    private Order order;
    private Execution execution;

    @BeforeEach
    void setUp() {
        service = new AllocationStateService(executions,
                new TradingMetrics(new SimpleMeterRegistry()),
                Clock.fixed(TradingFixtures.FIXED_NOW, ZoneOffset.UTC));
        order = TradingFixtures.order("ORD-1", "investor1", "100");
        execution = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        lenient().when(executions.findByExecRefIdIn(anyList())).thenReturn(List.of());
    }

    private AllocationStateCommand allocate(String allocationId, String... ids) {
        return new AllocationStateCommand(allocationId, AllocationStateCommand.Action.ALLOCATE, List.of(ids));
    }

    @Test
    void allocateBindsExecutionAndCountsTransition() {
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        AllocationStateService.Result result = service.apply(allocate("ALLOC-1", "exec_t1"));

        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(execution.getAllocationState()).isEqualTo(AllocationState.ALLOCATED);
        assertThat(execution.getAllocationId()).isEqualTo("ALLOC-1");
        assertThat(execution.getUpdatedAt()).isEqualTo(TradingFixtures.FIXED_NOW);
    }

    @Test
    void allocateReplayIsIdempotentNoOp() {
        execution.allocate("ALLOC-1", TradingFixtures.FIXED_NOW.minusSeconds(60));
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        AllocationStateService.Result result = service.apply(allocate("ALLOC-1", "exec_t1"));

        assertThat(result.appliedCount()).isZero();
        assertThat(execution.getAllocationId()).isEqualTo("ALLOC-1");
    }

    @Test
    void allocateToDifferentAllocationConflicts() {
        execution.allocate("ALLOC-1", TradingFixtures.FIXED_NOW.minusSeconds(60));
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        assertThatThrownBy(() -> service.apply(allocate("ALLOC-2", "exec_t1")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ALLOCATION_STATE_CONFLICT));
    }

    @Test
    void allocateRejectsCancelledExecution() {
        Execution cancellation = TradingFixtures.amendment(order, "exec_x1",
                ExecutionType.TRADE_CANCEL, execution, "0", null);
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_x1"))).thenReturn(List.of(cancellation));

        assertThatThrownBy(() -> service.apply(allocate("ALLOC-1", "exec_x1")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ALLOCATION_TARGET_INELIGIBLE));
    }

    @Test
    void allocateRejectsSupersededExecution() {
        Execution correction = TradingFixtures.amendment(order, "exec_c1",
                ExecutionType.TRADE_CORRECT, execution, "50", "10.50");
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));
        when(executions.findByExecRefIdIn(List.of("exec_t1"))).thenReturn(List.of(correction));

        assertThatThrownBy(() -> service.apply(allocate("ALLOC-1", "exec_t1")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ALLOCATION_TARGET_INELIGIBLE));
    }

    @Test
    void allocateFailsWhenAnyIdIsUnknown() {
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1", "exec_missing"))).thenReturn(List.of(execution));

        assertThatThrownBy(() -> service.apply(allocate("ALLOC-1", "exec_t1", "exec_missing")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("exec_missing");
    }

    @Test
    void releaseWithExplicitIdsUnbindsMatchingAllocation() {
        execution.allocate("ALLOC-1", TradingFixtures.FIXED_NOW.minusSeconds(60));
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        AllocationStateService.Result result = service.apply(new AllocationStateCommand(
                "ALLOC-1", AllocationStateCommand.Action.RELEASE, List.of("exec_t1")));

        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(execution.getAllocationState()).isEqualTo(AllocationState.UNALLOCATED);
        assertThat(execution.getAllocationId()).isNull();
    }

    @Test
    void releaseOfUnallocatedExecutionIsIdempotentNoOp() {
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        AllocationStateService.Result result = service.apply(new AllocationStateCommand(
                "ALLOC-1", AllocationStateCommand.Action.RELEASE, List.of("exec_t1")));

        assertThat(result.appliedCount()).isZero();
    }

    @Test
    void releaseConflictsWhenBoundToDifferentAllocation() {
        execution.allocate("ALLOC-OTHER", TradingFixtures.FIXED_NOW.minusSeconds(60));
        when(executions.findAllByInternalIdInForUpdate(List.of("exec_t1"))).thenReturn(List.of(execution));

        assertThatThrownBy(() -> service.apply(new AllocationStateCommand(
                "ALLOC-1", AllocationStateCommand.Action.RELEASE, List.of("exec_t1"))))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ALLOCATION_STATE_CONFLICT));
    }

    @Test
    void releaseWithoutIdsReleasesEverythingBoundToTheAllocation() {
        Execution second = TradingFixtures.trade(order, "exec_t2", "20", "11.00");
        execution.allocate("ALLOC-1", TradingFixtures.FIXED_NOW.minusSeconds(60));
        second.allocate("ALLOC-1", TradingFixtures.FIXED_NOW.minusSeconds(60));
        when(executions.findByAllocationId("ALLOC-1")).thenReturn(List.of(execution, second));

        AllocationStateService.Result result = service.apply(new AllocationStateCommand(
                "ALLOC-1", AllocationStateCommand.Action.RELEASE, null));

        assertThat(result.appliedCount()).isEqualTo(2);
        assertThat(execution.getAllocationState()).isEqualTo(AllocationState.UNALLOCATED);
        assertThat(second.getAllocationState()).isEqualTo(AllocationState.UNALLOCATED);
    }
}
