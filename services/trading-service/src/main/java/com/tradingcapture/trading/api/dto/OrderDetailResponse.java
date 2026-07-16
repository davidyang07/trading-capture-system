package com.tradingcapture.trading.api.dto;

import java.util.List;

/** Order detail: the order plus its complete execution history in write order. */
public record OrderDetailResponse(OrderResponse order, List<ExecutionResponse> executions) {
}
