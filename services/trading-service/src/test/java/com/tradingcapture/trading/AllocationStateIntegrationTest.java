package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Machine allocation-state API: idempotency, conflicts, eligibility, and authorization. */
class AllocationStateIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = TradingTestTokens.adminBearer();
    private static final String INVESTOR1 = TradingTestTokens.investorBearer("investor1");
    private static final String MACHINE = TradingTestTokens.machineBearer();

    private String allocateBody(String allocationId, String... executionIds) {
        return """
                { "allocationId": "%s", "action": "ALLOCATE", "executionInternalIds": [%s] }
                """.formatted(allocationId, String.join(",",
                java.util.Arrays.stream(executionIds).map(id -> "\"" + id + "\"").toList()));
    }

    private String releaseBody(String allocationId, String... executionIds) {
        if (executionIds.length == 0) {
            return """
                    { "allocationId": "%s", "action": "RELEASE" }
                    """.formatted(allocationId);
        }
        return """
                { "allocationId": "%s", "action": "RELEASE", "executionInternalIds": [%s] }
                """.formatted(allocationId, String.join(",",
                java.util.Arrays.stream(executionIds).map(id -> "\"" + id + "\"").toList()));
    }

    @Test
    void allocateIsIdempotentAndConflictsAcrossAllocations() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String execution = captureTrade(ADMIN, orderId, "60", "10.00");

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-1", execution)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.executions[0].allocationState").value("ALLOCATED"))
                .andExpect(jsonPath("$.executions[0].allocationId").value("ALLOC-IT-1"));

        // exact replay -> no-op success
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-1", execution)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(0));

        // a different allocation may not steal the execution
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-2", execution)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALLOCATION_STATE_CONFLICT"));
    }

    @Test
    void allocatedExecutionsAreProtectedFromAmendmentUntilReleased() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String execution = captureTrade(ADMIN, orderId, "60", "10.00");
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-1", execution)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/executions/" + execution + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "10.50" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_ALLOCATED"));

        // release-all (no explicit ids), then the correction is legal
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody("ALLOC-IT-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        mockMvc.perform(post("/api/v1/executions/" + execution + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "10.50" }
                                """))
                .andExpect(status().isCreated());

        // releasing an already-released allocation stays a no-op success
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody("ALLOC-IT-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(0));
    }

    @Test
    void cancelledAndSupersededExecutionsAreIneligibleForAllocation() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String superseded = captureTrade(ADMIN, orderId, "60", "10.00");
        String correctionBody = mockMvc.perform(post("/api/v1/executions/" + superseded + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "10.50" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String correction = com.jayway.jsonpath.JsonPath.read(correctionBody, "$.internalId");

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-3", superseded)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALLOCATION_TARGET_INELIGIBLE"));

        // the active tip is allocatable
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-3", correction)))
                .andExpect(status().isOk());
    }

    @Test
    void unknownExecutionIdsAre404() throws Exception {
        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MACHINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-4", "exec_does_not_exist")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_NOT_FOUND"));
    }

    @Test
    void machineEndpointRejectsUserTokensAndAnonymous() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String execution = captureTrade(ADMIN, orderId, "60", "10.00");

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-5", execution)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(put("/internal/v1/executions/allocation-state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocateBody("ALLOC-IT-5", execution)))
                .andExpect(status().isUnauthorized());
    }
}
