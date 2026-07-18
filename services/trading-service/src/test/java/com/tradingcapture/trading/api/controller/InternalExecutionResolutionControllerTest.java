package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.domain.service.ExecutionResolutionService;
import com.tradingcapture.trading.domain.service.ExecutionResolutionService.Reason;
import com.tradingcapture.trading.domain.service.ExecutionResolutionService.Resolution;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalExecutionResolutionController.class)
class InternalExecutionResolutionControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionResolutionService resolutionService;

    private static final String BODY = """
            { "execIds": ["EXEC-1"] }
            """;

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserTokenLacksMachineScopeAndGets403() throws Exception {
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void emptyExecIdsIs400() throws Exception {
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"execIds\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void machineTokenResolves200WithPerItemVerdicts() throws Exception {
        Resolution eligible = new Resolution("EXEC-1", true, "exec_t1", "ORD-1", "AAPL", "ACC-1",
                "1", new BigDecimal("60.0000"), new BigDecimal("60.0000"), "USD",
                LocalDate.parse("2026-07-17"), "0", LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-17T14:00:00Z"), "UNALLOCATED", null, true, Reason.ELIGIBLE);
        when(resolutionService.resolve(any())).thenReturn(List.of(eligible));

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .with(auth(TradingTestTokens.machineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].execId").value("EXEC-1"))
                .andExpect(jsonPath("$.results[0].internalId").value("exec_t1"))
                .andExpect(jsonPath("$.results[0].instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.results[0].side").value("1"))
                .andExpect(jsonPath("$.results[0].allocatableQuantity").value(60.0000))
                .andExpect(jsonPath("$.results[0].eligible").value(true))
                .andExpect(jsonPath("$.results[0].reason").value("ELIGIBLE"));
    }
}
