package com.tradingcapture.trading.api.mapper;

import com.tradingcapture.trading.api.dto.ExecutionResponse;
import com.tradingcapture.trading.api.dto.OrderDetailResponse;
import com.tradingcapture.trading.api.dto.OrderResponse;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Hand-written entity-to-DTO mapping (ADR-0014: no MapStruct, entities never serialized). */
@Component
public class OrderMapper {

    private final ExecutionMapper executionMapper;

    public OrderMapper(ExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getOrderId(),
                order.getClOrdId(),
                order.getOwnerSubject(),
                order.getAccountId(),
                order.getInstrumentId(),
                order.getStatus(),
                order.getSide(),
                order.getOrderType(),
                order.getPrice(),
                order.getPriceType(),
                order.getCurrency(),
                order.getQuantity(),
                order.getCumQty(),
                order.getLeavesQty(),
                order.getSettleType(),
                order.getSettleDate(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion());
    }

    public OrderDetailResponse toDetailResponse(Order order, List<Execution> executions) {
        List<ExecutionResponse> executionResponses = executions.stream()
                .map(executionMapper::toResponse)
                .toList();
        return new OrderDetailResponse(toResponse(order), executionResponses);
    }
}
