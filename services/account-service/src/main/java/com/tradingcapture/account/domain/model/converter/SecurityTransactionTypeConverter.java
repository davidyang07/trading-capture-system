package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.SecurityTransactionType;
import jakarta.persistence.Converter;

@Converter
public class SecurityTransactionTypeConverter extends CodeEnumConverter<SecurityTransactionType> {

    public SecurityTransactionTypeConverter() {
        super(SecurityTransactionType.class);
    }
}
