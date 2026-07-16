package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.service.AllocationStateService;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllocationStateController.class)
class AllocationStateControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AllocationStateService allocationStateService;

    private static final String ALLOCATE_BODY = """
            { "allocationId": "ALLOC-1", "action": "ALLOCATE", "executionInternalIds": ["exec_t1"] }
            """;

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .contentType(MediaType.APPLICATION_JSON).content(ALLOCATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserTokenLacksMachineScopeAndGets403() throws Exception {
        // /internal/** is machine territory: even the ADMIN role does not carry
        // allocation:process, so user tokens are rejected at the chain level.
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(ALLOCATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void machineTokenApplies200() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution execution = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        execution.allocate("ALLOC-1", TradingFixtures.FIXED_NOW);
        when(allocationStateService.apply(any())).thenReturn(new AllocationStateService.Result(
                "ALLOC-1", AllocationStateCommand.Action.ALLOCATE, 1, List.of(execution)));

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(ALLOCATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.executions[0].allocationState").value("ALLOCATED"))
                .andExpect(jsonPath("$.executions[0].allocationId").value("ALLOC-1"));
    }

    @Test
    void allocateWithoutExecutionIdsIs400() throws Exception {
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "allocationId": "ALLOC-1", "action": "ALLOCATE" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        when(allocationStateService.apply(any())).thenThrow(
                new BusinessRuleException(ErrorCode.ALLOCATION_STATE_CONFLICT, "already allocated"));

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(ALLOCATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALLOCATION_STATE_CONFLICT"));
    }
}
