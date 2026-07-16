package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack execution capture and append-only correction/cancellation chains — the core
 * Phase 3 behavior, verified through the real HTTP + security + JPA + MySQL stack.
 */
class ExecutionWorkflowIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = TradingTestTokens.adminBearer();
    private static final String INVESTOR1 = TradingTestTokens.investorBearer("investor1");
    private static final String INVESTOR2 = TradingTestTokens.investorBearer("investor2");

    @Test
    void fillsDriveDerivedAggregatesAndOrderStatusTransitions() throws Exception {
        String orderId = createOrder(INVESTOR1, "MSFT", "120");

        // first partial fill: 80 @ 415.10
        String firstFill = captureTrade(ADMIN, orderId, "80", "415.10");
        mockMvc.perform(get("/api/v1/executions/" + firstFill)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.cumQty").value(80.0))
                .andExpect(jsonPath("$.execution.leavesQty").value(40.0))
                .andExpect(jsonPath("$.execution.avgPx").value(415.10))
                .andExpect(jsonPath("$.execution.ordStatus").value("PARTIALLY_FILLED"));

        // second fill 40 @ 415.35 fills the order; avgPx is quantity-weighted at scale 8
        captureTrade(ADMIN, orderId, "40", "415.35");
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FILLED"))
                .andExpect(jsonPath("$.order.cumQty").value(120.0))
                .andExpect(jsonPath("$.order.leavesQty").value(0.0))
                .andExpect(jsonPath("$.executions[1].avgPx").value(415.18333333));

        // no further capture on a filled order
        mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s", "lastQty": "1", "lastPx": "1.00" }
                                """.formatted(orderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_ALREADY_FILLED"));
    }

    @Test
    void overfillIsRejectedBeforeAnyStateChanges() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        captureTrade(ADMIN, orderId, "70", "10.00");

        mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s", "lastQty": "31", "lastPx": "10.00" }
                                """.formatted(orderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_OVERFILL"));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(jsonPath("$.order.cumQty").value(70.0))
                .andExpect(jsonPath("$.executions.length()").value(1));
    }

    @Test
    void multiStepCorrectionChainKeepsRootAndHistoryIntact() throws Exception {
        String orderId = createOrder(INVESTOR1, "TSLA", "150");
        String trade = captureTrade(ADMIN, orderId, "150", "240.00");

        // first correction: restate the price
        String firstCorrectionBody = mockMvc.perform(post("/api/v1/executions/" + trade + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "150", "lastPx": "241.00", "text": "price fix" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execType").value("TRADE_CORRECT"))
                .andExpect(jsonPath("$.execRefId").value(trade))
                .andExpect(jsonPath("$.rootExecutionId").value(trade))
                .andReturn().getResponse().getContentAsString();
        String firstCorrection = com.jayway.jsonpath.JsonPath.read(firstCorrectionBody, "$.internalId");

        // correcting the superseded original is illegal
        mockMvc.perform(post("/api/v1/executions/" + trade + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "100", "lastPx": "239.00" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_TARGET_SUPERSEDED"));

        // second correction targets the chain tip; root still resolves to the original trade
        String secondCorrectionBody = mockMvc.perform(post("/api/v1/executions/" + firstCorrection + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "140", "lastPx": "241.50", "text": "qty fix" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execRefId").value(firstCorrection))
                .andExpect(jsonPath("$.rootExecutionId").value(trade))
                .andExpect(jsonPath("$.cumQty").value(140.0))
                .andExpect(jsonPath("$.ordStatus").value("PARTIALLY_FILLED"))
                .andReturn().getResponse().getContentAsString();
        String secondCorrection = com.jayway.jsonpath.JsonPath.read(secondCorrectionBody, "$.internalId");

        // the order reflects only the active tip
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(jsonPath("$.order.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.order.cumQty").value(140.0))
                .andExpect(jsonPath("$.order.leavesQty").value(10.0))
                .andExpect(jsonPath("$.executions.length()").value(3));

        // detail of ANY chain member exposes the whole chain with the tip marked
        mockMvc.perform(get("/api/v1/executions/" + trade)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainRootExecutionId").value(trade))
                .andExpect(jsonPath("$.chainActive").value(true))
                .andExpect(jsonPath("$.chain.length()").value(3))
                .andExpect(jsonPath("$.chain[0].internalId").value(trade))
                .andExpect(jsonPath("$.chain[0].tip").value(false))
                .andExpect(jsonPath("$.chain[2].internalId").value(secondCorrection))
                .andExpect(jsonPath("$.chain[2].tip").value(true))
                // the historical record itself is untouched
                .andExpect(jsonPath("$.execution.lastPx").value(240.00))
                .andExpect(jsonPath("$.execution.execType").value("TRADE"));
    }

    @Test
    void cancellationVoidsTheChainAndRecomputesTheOrder() throws Exception {
        String orderId = createOrder(INVESTOR1, "AMZN", "300");
        String keptFill = captureTrade(ADMIN, orderId, "200", "178.00");
        String cancelledFill = captureTrade(ADMIN, orderId, "100", "178.55");

        mockMvc.perform(post("/api/v1/executions/" + cancelledFill + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "text": "busted trade" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execType").value("TRADE_CANCEL"))
                .andExpect(jsonPath("$.lastQty").value(0.0))
                .andExpect(jsonPath("$.cumQty").value(200.0));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(jsonPath("$.order.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.order.cumQty").value(200.0))
                .andExpect(jsonPath("$.order.leavesQty").value(100.0));

        // the cancelled chain is inactive; the untouched chain still contributes
        mockMvc.perform(get("/api/v1/executions/" + cancelledFill)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.chainActive").value(false));
        mockMvc.perform(get("/api/v1/executions/" + keptFill)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.chainActive").value(true));

        // amendments of any cancelled-chain member are illegal
        mockMvc.perform(post("/api/v1/executions/" + cancelledFill + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "50", "lastPx": "178.00" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_TARGET_SUPERSEDED"));

        // leaves freed by the cancellation can be refilled
        captureTrade(ADMIN, orderId, "100", "179.00");
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(jsonPath("$.order.status").value("FILLED"));
    }

    @Test
    void correctionCanOnlyRestateWithinTheOrderQuantity() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String trade = captureTrade(ADMIN, orderId, "60", "10.00");
        captureTrade(ADMIN, orderId, "30", "10.00");

        // 60 -> 71 would make cum 101 > 100
        mockMvc.perform(post("/api/v1/executions/" + trade + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "71", "lastPx": "10.00" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_OVERFILL"));

        // 60 -> 70 exactly fills
        mockMvc.perform(post("/api/v1/executions/" + trade + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "70", "lastPx": "10.00" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordStatus").value("FILLED"));
    }

    @Test
    void executionSearchFiltersAndIsolatesOwnership() throws Exception {
        String order1 = createOrder(INVESTOR1, "AAPL", "100");
        String order2 = createOrder(INVESTOR2, "MSFT", "100");
        captureTrade(ADMIN, order1, "60", "10.00");
        String cancelTarget = captureTrade(ADMIN, order1, "20", "11.00");
        captureTrade(ADMIN, order2, "50", "20.00");
        mockMvc.perform(post("/api/v1/executions/" + cancelTarget + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isCreated());

        // investor1 sees only executions of their own orders (3 = 2 fills + 1 cancel row)
        mockMvc.perform(get("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.totalElements").value(4));

        mockMvc.perform(get("/api/v1/executions").param("execType", "TRADE_CANCEL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/executions").param("orderId", order2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(order2));

        // search is owner-isolated: investor1 filtering by investor2's order sees nothing
        mockMvc.perform(get("/api/v1/executions").param("orderId", order2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void executionDetailIsOwnershipMaskedAsNotFound() throws Exception {
        String order1 = createOrder(INVESTOR1, "AAPL", "100");
        String execId = captureTrade(ADMIN, order1, "60", "10.00");

        // the owner can read the detail
        mockMvc.perform(get("/api/v1/executions/" + execId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.internalId").value(execId));

        // a different investor is masked with the SAME response as a missing execution:
        // 404 + EXECUTION_NOT_FOUND — never ORDER_NOT_FOUND, which would reveal that the
        // execution exists but belongs to someone else (defeats 404 masking).
        mockMvc.perform(get("/api/v1/executions/" + execId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_NOT_FOUND"));

        // identical shape to a genuinely non-existent execution
        mockMvc.perform(get("/api/v1/executions/NO-SUCH-EXEC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXECUTION_NOT_FOUND"));
    }

    @Test
    void feesArePersistedAndRestatedByCorrections() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String body = mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s", "lastQty": "60", "lastPx": "10.00",
                                  "miscFees": [ { "feeType": "REGULATORY", "amount": "0.04",
                                                  "currency": "USD", "basis": "ABSOLUTE" } ] }
                                """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String trade = com.jayway.jsonpath.JsonPath.read(body, "$.internalId");

        mockMvc.perform(get("/api/v1/executions/" + trade)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.miscFees.length()").value(1))
                .andExpect(jsonPath("$.miscFees[0].feeType").value("REGULATORY"));

        // correction without a fee list inherits the target's fees
        String correctionBody = mockMvc.perform(post("/api/v1/executions/" + trade + "/correct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lastQty": "60", "lastPx": "10.50" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String correction = com.jayway.jsonpath.JsonPath.read(correctionBody, "$.internalId");

        mockMvc.perform(get("/api/v1/executions/" + correction)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(jsonPath("$.miscFees.length()").value(1))
                .andExpect(jsonPath("$.miscFees[0].feeType").value("REGULATORY"));
    }
}
