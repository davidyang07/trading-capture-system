package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.InstrumentType;
import jakarta.persistence.Converter;

@Converter
public class InstrumentTypeConverter extends CodeEnumConverter<InstrumentType> {

    public InstrumentTypeConverter() {
        super(InstrumentType.class);
    }
}
