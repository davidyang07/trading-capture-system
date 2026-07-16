package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-stack order lifecycle: ownership isolation, eligibility rules, deterministic search. */
class OrderLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = TradingTestTokens.adminBearer();
    private static final String INVESTOR1 = TradingTestTokens.investorBearer("investor1");
    private static final String INVESTOR2 = TradingTestTokens.investorBearer("investor2");

    @Test
    void investorOwnsWhatTheyCreateAndOtherInvestorsCannotSeeIt() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.ownerSubject").value("investor1"))
                .andExpect(jsonPath("$.order.status").value("NEW"))
                .andExpect(jsonPath("$.executions").isEmpty());

        // masked as 404 for the wrong owner — existence is not revealed
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

        // administrators see everything
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void wrongOwnerMutationsAreMaskedAndDoNothing() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");

        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("MSFT", "200")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.instrumentId").value("AAPL"));
    }

    @Test
    void duplicateOrderIdIsRejectedWith409() throws Exception {
        String body = """
                {
                  "orderId": "ORD-IT-DUP",
                  "instrumentId": "AAPL", "accountId": "ACC-IT-1", "side": "BUY",
                  "orderType": "LIMIT", "price": "10.00", "priceType": "PER_UNIT",
                  "currency": "USD", "quantity": "100", "settleType": "T_PLUS_2",
                  "settleDate": "2026-07-20"
                }
                """;
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR2)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ORDER_ID"));
    }

    @Test
    void updateAndDeleteAreAllowedOnlyWhileNew() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");

        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("MSFT", "250")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("MSFT"))
                .andExpect(jsonPath("$.quantity").value(250.0))
                .andExpect(jsonPath("$.leavesQty").value(250.0));

        captureTrade(ADMIN, orderId, "50", "10.00");

        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("MSFT", "300")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_ELIGIBLE"));
        mockMvc.perform(delete("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_ELIGIBLE"));
    }

    @Test
    void cancelledOutOrderIsNewAgainButItsHistoryBlocksDeletion() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");
        String executionId = captureTrade(ADMIN, orderId, "50", "10.00");
        mockMvc.perform(post("/api/v1/executions/" + executionId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("NEW"))
                .andExpect(jsonPath("$.order.cumQty").value(0.0))
                .andExpect(jsonPath("$.executions.length()").value(2));

        // NEW again, but the immutable audit history must survive — deletion refused.
        mockMvc.perform(delete("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_HAS_EXECUTIONS"));
    }

    @Test
    void deleteRemovesUntouchedNewOrder() throws Exception {
        String orderId = createOrder(INVESTOR1, "AAPL", "100");

        mockMvc.perform(delete("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchIsolatesInvestorsAndSupportsAdminOwnerFilter() throws Exception {
        createOrder(INVESTOR1, "AAPL", "100");
        createOrder(INVESTOR1, "MSFT", "200");
        createOrder(INVESTOR2, "AAPL", "300");

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].ownerSubject",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("investor1"))));

        // the ownerSubject filter cannot break isolation for investors
        mockMvc.perform(get("/api/v1/orders").param("ownerSubject", "investor2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].ownerSubject",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("investor1"))));

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/orders").param("ownerSubject", "investor2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ownerSubject").value("investor2"));

        mockMvc.perform(get("/api/v1/orders").param("instrumentId", "AAPL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchSortsDeterministically() throws Exception {
        String first = createOrder(INVESTOR1, "AAPL", "100");
        String second = createOrder(INVESTOR1, "MSFT", "200");

        mockMvc.perform(get("/api/v1/orders")
                        .param("sortBy", "ORDER_ID").param("sortDir", "ASC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVESTOR1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId")
                        .value(first.compareTo(second) < 0 ? first : second));
    }
}
