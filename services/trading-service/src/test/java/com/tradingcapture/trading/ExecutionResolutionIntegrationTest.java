package com.tradingcapture.trading;

import com.jayway.jsonpath.JsonPath;
import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Machine execution-resolution API: ExecID → internal id + allocation eligibility, per-item
 * verdicts, and authorization. Closes the Phase 8 contract gap (FIX NoExecs carries ExecID,
 * allocation binds by internal id).
 */
class ExecutionResolutionIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = TradingTestTokens.adminBearer();
    private static final String INVESTOR1 = TradingTestTokens.investorBearer("investor1");
    private static final String MACHINE = TradingTestTokens.machineBearer();

    private record Captured(String internalId, String execId) {
    }

    private Captured capture(String orderId, String lastQty, String lastPx) throws Exception {
        String body = mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s", "lastQty": "%s", "lastPx": "%s" }
                                """.formatted(orderId, lastQty, lastPx)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Captured(JsonPath.read(body, "$.internalId"), JsonPath.read(body, "$.execId"));
    }

    private String resolveBody(String... execIds) {
        return """
                { "execIds": [%s] }
                """.formatted(String.join(",",
                java.util.Arrays.stream(execIds).map(id -> "\"" + id + "\"").toList()));
    }

    private void allocate(String allocationId, String internalId) throws Exception {
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "allocationId": "%s", "action": "ALLOCATE", "executionInternalIds": ["%s"] }
                                """.formatted(allocationId, internalId)))
                .andExpect(status().isOk());
    }

    @Test
    void eligibleTradeResolvesWithFieldsAndAllocatableQuantity() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        Captured trade = capture(orderId, "60", "10.00");

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(trade.execId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].found").value(true))
                .andExpect(jsonPath("$.results[0].internalId").value(trade.internalId()))
                .andExpect(jsonPath("$.results[0].orderId").value(orderId))
                .andExpect(jsonPath("$.results[0].instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.results[0].side").value("1"))
                .andExpect(jsonPath("$.results[0].executedQuantity").value(60.0000))
                .andExpect(jsonPath("$.results[0].allocatableQuantity").value(60.0000))
                .andExpect(jsonPath("$.results[0].allocationState").value("UNALLOCATED"))
                .andExpect(jsonPath("$.results[0].eligible").value(true))
                .andExpect(jsonPath("$.results[0].reason").value("ELIGIBLE"));
    }

    @Test
    void allocatedExecutionResolvesAsAlreadyAllocatedWithZeroAllocatable() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        Captured trade = capture(orderId, "60", "10.00");
        allocate("ALLOC-RES-1", trade.internalId());

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(trade.execId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].eligible").value(false))
                .andExpect(jsonPath("$.results[0].reason").value("ALREADY_ALLOCATED"))
                .andExpect(jsonPath("$.results[0].allocationId").value("ALLOC-RES-1"))
                .andExpect(jsonPath("$.results[0].allocatableQuantity").value(0));
    }

    @Test
    void supersededExecutionIsIneligibleWhileTheCorrectionTipIsEligible() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        Captured original = capture(orderId, "60", "10.00");

        String correctionBody = mockMvc.perform(post("/api/v1/executions/" + original.internalId() + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"lastQty\": \"50\", \"lastPx\": \"10.50\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tipExecId = JsonPath.read(correctionBody, "$.execId");

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(original.execId(), tipExecId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].reason").value("SUPERSEDED"))
                .andExpect(jsonPath("$.results[0].eligible").value(false))
                .andExpect(jsonPath("$.results[1].reason").value("ELIGIBLE"))
                .andExpect(jsonPath("$.results[1].eligible").value(true));
    }

    @Test
    void unknownExecIdResolvesAsNotFoundWithoutFailingTheBatch() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        Captured trade = capture(orderId, "60", "10.00");

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("DOES-NOT-EXIST", trade.execId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].found").value(false))
                .andExpect(jsonPath("$.results[0].reason").value("NOT_FOUND"))
                .andExpect(jsonPath("$.results[1].found").value(true))
                .andExpect(jsonPath("$.results[1].reason").value("ELIGIBLE"));
    }

    @Test
    void investorAndAnonymousCannotResolve() throws Exception {
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("EXEC-X")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("EXEC-X")))
                .andExpect(status().isUnauthorized());
    }
}
