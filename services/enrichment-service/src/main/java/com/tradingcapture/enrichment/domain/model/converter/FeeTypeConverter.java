package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.FeeType;
import jakarta.persistence.Converter;

@Converter
public class FeeTypeConverter extends CodeEnumConverter<FeeType> {

    public FeeTypeConverter() {
        super(FeeType.class);
    }
}
