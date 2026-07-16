package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.PriceType;
import jakarta.persistence.Converter;

@Converter
public class PriceTypeConverter extends CodeEnumConverter<PriceType> {

    public PriceTypeConverter() {
        super(PriceType::code, PriceType::fromCode);
    }
}
