package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.OrderType;
import jakarta.persistence.Converter;

@Converter
public class OrderTypeConverter extends CodeEnumConverter<OrderType> {

    public OrderTypeConverter() {
        super(OrderType::code, OrderType::fromCode);
    }
}
