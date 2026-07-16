package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.AllocationState;
import jakarta.persistence.Converter;

@Converter
public class AllocationStateConverter extends CodeEnumConverter<AllocationState> {

    public AllocationStateConverter() {
        super(AllocationState::code, AllocationState::fromCode);
    }
}
