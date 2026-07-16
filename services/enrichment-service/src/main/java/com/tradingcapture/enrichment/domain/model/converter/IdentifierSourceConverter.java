package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import jakarta.persistence.Converter;

@Converter
public class IdentifierSourceConverter extends CodeEnumConverter<IdentifierSource> {

    public IdentifierSourceConverter() {
        super(IdentifierSource.class);
    }
}
