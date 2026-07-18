package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import jakarta.persistence.Converter;

@Converter
public class ValidationErrorCodeConverter extends CodeEnumConverter<ValidationErrorCode> {

    public ValidationErrorCodeConverter() {
        super(ValidationErrorCode.class);
    }
}
