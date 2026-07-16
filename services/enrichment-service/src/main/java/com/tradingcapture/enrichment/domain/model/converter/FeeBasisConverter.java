package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.FeeBasis;
import jakarta.persistence.Converter;

@Converter
public class FeeBasisConverter extends CodeEnumConverter<FeeBasis> {

    public FeeBasisConverter() {
        super(FeeBasis.class);
    }
}
