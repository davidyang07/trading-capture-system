package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.FailureCategory;
import jakarta.persistence.Converter;

@Converter
public class FailureCategoryConverter extends CodeEnumConverter<FailureCategory> {

    public FailureCategoryConverter() {
        super(FailureCategory.class);
    }
}
