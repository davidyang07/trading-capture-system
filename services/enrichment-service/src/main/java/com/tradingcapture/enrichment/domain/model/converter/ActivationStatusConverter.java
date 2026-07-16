package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import jakarta.persistence.Converter;

@Converter
public class ActivationStatusConverter extends CodeEnumConverter<ActivationStatus> {

    public ActivationStatusConverter() {
        super(ActivationStatus.class);
    }
}
