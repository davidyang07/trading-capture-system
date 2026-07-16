package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.mapper.ExecutionMapper;
import com.tradingcapture.trading.api.mapper.OrderMapper;
import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.domain.service.ExecutionQueryService;
import com.tradingcapture.trading.domain.service.OrderService;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({OrderMapper.class, ExecutionMapper.class})
class OrderControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private ExecutionQueryService executionQueryService;

    private static final String VALID_CREATE_BODY = """
            {
              "instrumentId": "AAPL",
              "accountId": "ACC-1",
              "side": "BUY",
              "orderType": "LIMIT",
              "price": "189.25",
              "priceType": "PER_UNIT",
              "currency": "USD",
              "quantity": "100",
              "settleType": "T_PLUS_2",
              "settleDate": "2026-07-03"
            }
            """;

    @Test
    void anonymousRequestsGet401ProblemResponse() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void missingScopeGets403ProblemResponse() throws Exception {
        // token with only read scope must not create orders
        var readOnly = com.tradingcapture.testsupport.TestJwts.jwt("sub-x", "reader",
                List.of("INVESTOR"), "order:read", List.of(TradingTestTokens.AUDIENCE));

        mockMvc.perform(post("/api/v1/orders").with(auth(readOnly))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        when(orderService.create(any(), any())).thenReturn(order);

        mockMvc.perform(post("/api/v1/orders").with(auth(TradingTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/ORD-1"))
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.ownerSubject").value("investor1"));

        ArgumentCaptor<AccessContext> ctx = ArgumentCaptor.forClass(AccessContext.class);
        verify(orderService).create(ctx.capture(), any());
        assertThat(ctx.getValue().subject()).isEqualTo("investor1");
        assertThat(ctx.getValue().admin()).isFalse();
    }

    @Test
    void createValidationFailureListsFieldErrors() throws Exception {
        String invalidBody = """
                { "instrumentId": "AAPL", "accountId": "ACC-1", "price": "-1" }
                """;

        mockMvc.perform(post("/api/v1/orders").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void malformedEnumValueIs400() throws Exception {
        String badEnum = VALID_CREATE_BODY.replace("\"BUY\"", "\"LONG\"");

        mockMvc.perform(post("/api/v1/orders").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(badEnum))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void duplicateOrderIdMapsTo409() throws Exception {
        when(orderService.create(any(), any())).thenThrow(
                new BusinessRuleException(ErrorCode.DUPLICATE_ORDER_ID, "Order ID already in use: ORD-1"));

        mockMvc.perform(post("/api/v1/orders").with(auth(TradingTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ORDER_ID"));
    }

    @Test
    void unknownOrMaskedOrderMapsTo404() throws Exception {
        when(orderService.get(any(), eq("ORD-X"))).thenThrow(ResourceNotFoundException.order("ORD-X"));

        mockMvc.perform(get("/api/v1/orders/ORD-X").with(auth(TradingTestTokens.investorJwt("investor1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    void searchBindsFiltersAndReturnsPageEnvelope() throws Exception {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100.0000");
        when(orderService.search(any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(order), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/v1/orders")
                        .param("side", "BUY").param("status", "NEW")
                        .param("page", "0").param("size", "5")
                        .param("sortBy", "PRICE").param("sortDir", "ASC")
                        .with(auth(TradingTestTokens.investorJwt("investor1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value("ORD-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).search(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).contains("price: ASC").contains("orderId: ASC");
    }

    @Test
    void searchRejectsInvalidSortField() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("sortBy", "ownerSubject")
                        .with(auth(TradingTestTokens.adminJwt())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("size", "1000")
                        .with(auth(TradingTestTokens.adminJwt())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateNotEligibleMapsTo409() throws Exception {
        when(orderService.update(any(), eq("ORD-1"), any())).thenThrow(
                new BusinessRuleException(ErrorCode.ORDER_NOT_ELIGIBLE, "not NEW"));

        mockMvc.perform(put("/api/v1/orders/ORD-1").with(auth(TradingTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_ELIGIBLE"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/ORD-1").with(auth(TradingTestTokens.investorJwt("investor1"))))
                .andExpect(status().isNoContent());
        verify(orderService).delete(any(), eq("ORD-1"));
    }

    @Test
    void deleteWithExecutionHistoryMapsTo409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessRuleException(ErrorCode.ORDER_HAS_EXECUTIONS, "has executions"))
                .when(orderService).delete(any(), eq("ORD-1"));

        mockMvc.perform(delete("/api/v1/orders/ORD-1").with(auth(TradingTestTokens.adminJwt())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_HAS_EXECUTIONS"));
    }
}
