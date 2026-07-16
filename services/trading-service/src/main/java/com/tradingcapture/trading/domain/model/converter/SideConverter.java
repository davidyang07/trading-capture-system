package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.Side;
import jakarta.persistence.Converter;

@Converter
public class SideConverter extends CodeEnumConverter<Side> {

    public SideConverter() {
        super(Side::code, Side::fromCode);
    }
}
