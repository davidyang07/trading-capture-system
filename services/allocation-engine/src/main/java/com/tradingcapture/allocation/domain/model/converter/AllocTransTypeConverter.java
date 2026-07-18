package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import jakarta.persistence.Converter;

@Converter
public class AllocTransTypeConverter extends CodeEnumConverter<AllocTransType> {

    public AllocTransTypeConverter() {
        super(AllocTransType.class);
    }
}
