package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.SettleType;
import jakarta.persistence.Converter;

@Converter
public class SettleTypeConverter extends CodeEnumConverter<SettleType> {

    public SettleTypeConverter() {
        super(SettleType::code, SettleType::fromCode);
    }
}
