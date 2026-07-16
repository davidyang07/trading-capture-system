package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ownership masking of {@link ExecutionQueryService#getDetail} at the service level (the fast
 * pyramid tier) — the full HTTP+MySQL path is additionally covered by
 * {@code ExecutionWorkflowIntegrationTest.executionDetailIsOwnershipMaskedAsNotFound}.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionQueryServiceTest {

    @Mock
    private ExecutionRepository executions;

    private ExecutionQueryService service;
    private Order order;
    private Execution execution;

    @BeforeEach
    void setUp() {
        service = new ExecutionQueryService(executions);
        order = TradingFixtures.order("ORD-1", "investor1", "100");
        execution = TradingFixtures.trade(order, "exec_1", "60", "10.00");
        lenient().when(executions.findById("exec_1")).thenReturn(Optional.of(execution));
        lenient().when(executions.findByRootExecutionIdOrderByCreatedAtAscInternalIdAsc("exec_1"))
                .thenReturn(List.of(execution));
    }

    @Test
    void ownerSeesTheDetailAndItsChain() {
        ExecutionQueryService.ExecutionWithChain detail =
                service.getDetail(TradingFixtures.investor("investor1"), "exec_1");

        assertThat(detail.execution().getInternalId()).isEqualTo("exec_1");
        assertThat(detail.chain()).containsExactly(execution);
    }

    @Test
    void adminSeesAnyExecution() {
        assertThat(service.getDetail(TradingFixtures.admin(), "exec_1").execution())
                .isSameAs(execution);
    }

    @Test
    void foreignInvestorIsMaskedAsExecutionNotFound() {
        // The mask must report the requested resource type (EXECUTION_NOT_FOUND) — never
        // ORDER_NOT_FOUND — so an unowned execution is indistinguishable from a missing one.
        assertThatThrownBy(() -> service.getDetail(TradingFixtures.investor("investor2"), "exec_1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).errorCode())
                .isEqualTo(ErrorCode.EXECUTION_NOT_FOUND);
    }

    @Test
    void missingExecutionIsExecutionNotFound() {
        when(executions.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(TradingFixtures.investor("investor2"), "nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).errorCode())
                .isEqualTo(ErrorCode.EXECUTION_NOT_FOUND);
    }
}
