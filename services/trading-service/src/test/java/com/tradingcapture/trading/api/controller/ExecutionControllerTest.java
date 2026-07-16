package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.mapper.ExecutionMapper;
import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.service.ExecutionQueryService;
import com.tradingcapture.trading.domain.service.ExecutionQueryService.ExecutionWithChain;
import com.tradingcapture.trading.domain.service.ExecutionService;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
@Import(ExecutionMapper.class)
class ExecutionControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionService executionService;
    @MockitoBean
    private ExecutionQueryService executionQueryService;

    private static final String VALID_CREATE_BODY = """
            { "orderId": "ORD-1", "lastQty": "60", "lastPx": "10.00" }
            """;

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void investorWithWriteScopeButWithoutAdminRoleCannotCapture() throws Exception {
        // Investor tokens carry execution:write (client default scopes) — the ADMIN role
        // requirement is what keeps execution capture an operational function.
        mockMvc.perform(post("/api/v1/executions").with(auth(TradingTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void machineTokenWithoutAdminRoleCannotCapture() throws Exception {
        mockMvc.perform(post("/api/v1/executions").with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCaptures201WithLocation() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution execution = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        when(executionService.createTrade(any(), any())).thenReturn(execution);

        mockMvc.perform(post("/api/v1/executions").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/executions/exec_t1"))
                .andExpect(jsonPath("$.internalId").value("exec_t1"))
                .andExpect(jsonPath("$.cumQty").value(60))
                .andExpect(jsonPath("$.execType").value("TRADE"));
    }

    @Test
    void captureValidationRejectsNonPositiveQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/executions").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "ORD-1", "lastQty": "0", "lastPx": "10.00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void overfillMapsTo409() throws Exception {
        when(executionService.createTrade(any(), any())).thenThrow(
                new BusinessRuleException(ErrorCode.EXECUTION_OVERFILL, "overfill"));

        mockMvc.perform(post("/api/v1/executions").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_OVERFILL"));
    }

    @Test
    void investorCanReadDetailOfOwnedExecution() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution execution = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        when(executionQueryService.getDetail(any(), eq("exec_t1")))
                .thenReturn(new ExecutionWithChain(execution, List.of(execution)));

        mockMvc.perform(get("/api/v1/executions/exec_t1")
                        .with(auth(TradingTestTokens.investorJwt("investor1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.internalId").value("exec_t1"))
                .andExpect(jsonPath("$.chainActive").value(true))
                .andExpect(jsonPath("$.chain[0].tip").value(true));
    }

    @Test
    void correctRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/executions/exec_t1/correct")
                        .with(auth(TradingTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "10.50" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctSupersededTargetMapsTo409() throws Exception {
        when(executionService.correct(any(), eq("exec_t1"), any())).thenThrow(
                new BusinessRuleException(ErrorCode.EXECUTION_TARGET_SUPERSEDED, "superseded"));

        mockMvc.perform(post("/api/v1/executions/exec_t1/correct")
                        .with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "10.50" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_TARGET_SUPERSEDED"));
    }

    @Test
    void cancelAcceptsMissingBody() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        Execution trade = TradingFixtures.trade(order, "exec_t1", "60", "10.00");
        Execution cancellation = TradingFixtures.amendment(order, "exec_x1",
                ExecutionType.TRADE_CANCEL, trade, "0", null);
        when(executionService.cancel(any(), eq("exec_t1"), any())).thenReturn(cancellation);

        mockMvc.perform(post("/api/v1/executions/exec_t1/cancel")
                        .with(auth(TradingTestTokens.adminJwt())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execType").value("TRADE_CANCEL"));
        verify(executionService).cancel(any(), eq("exec_t1"), any());
    }

    @Test
    void searchValidatesDateRangeSemantics() throws Exception {
        when(executionQueryService.search(any(), any(), any())).thenThrow(
                new com.tradingcapture.trading.domain.exception.InvalidRequestException(
                        "tradeDateFrom must not be after tradeDateTo"));

        mockMvc.perform(get("/api/v1/executions")
                        .param("tradeDateFrom", "2026-07-10").param("tradeDateTo", "2026-07-01")
                        .with(auth(TradingTestTokens.adminJwt())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
