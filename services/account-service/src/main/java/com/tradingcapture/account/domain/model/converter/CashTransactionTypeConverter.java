package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.CashTransactionType;
import jakarta.persistence.Converter;

@Converter
public class CashTransactionTypeConverter extends CodeEnumConverter<CashTransactionType> {

    public CashTransactionTypeConverter() {
        super(CashTransactionType.class);
    }
}
