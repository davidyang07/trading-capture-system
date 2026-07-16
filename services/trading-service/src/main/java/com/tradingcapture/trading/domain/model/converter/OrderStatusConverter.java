package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.OrderStatus;
import jakarta.persistence.Converter;

@Converter
public class OrderStatusConverter extends CodeEnumConverter<OrderStatus> {

    public OrderStatusConverter() {
        super(OrderStatus::code, OrderStatus::fromCode);
    }
}
