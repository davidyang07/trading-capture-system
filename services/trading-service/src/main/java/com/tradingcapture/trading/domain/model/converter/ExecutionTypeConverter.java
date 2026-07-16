package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.ExecutionType;
import jakarta.persistence.Converter;

@Converter
public class ExecutionTypeConverter extends CodeEnumConverter<ExecutionType> {

    public ExecutionTypeConverter() {
        super(ExecutionType::code, ExecutionType::fromCode);
    }
}
