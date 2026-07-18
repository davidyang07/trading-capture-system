package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import jakarta.persistence.Converter;

@Converter
public class OrchestrationFailureCategoryConverter extends CodeEnumConverter<OrchestrationFailureCategory> {

    public OrchestrationFailureCategoryConverter() {
        super(OrchestrationFailureCategory.class);
    }
}
