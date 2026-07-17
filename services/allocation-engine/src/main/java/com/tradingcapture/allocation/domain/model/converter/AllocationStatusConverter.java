package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.AllocationStatus;
import jakarta.persistence.Converter;

@Converter
public class AllocationStatusConverter extends CodeEnumConverter<AllocationStatus> {

    public AllocationStatusConverter() {
        super(AllocationStatus.class);
    }
}
