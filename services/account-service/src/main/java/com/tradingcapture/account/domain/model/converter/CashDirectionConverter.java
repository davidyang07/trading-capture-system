package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.CashDirection;
import jakarta.persistence.Converter;

@Converter
public class CashDirectionConverter extends CodeEnumConverter<CashDirection> {

    public CashDirectionConverter() {
        super(CashDirection.class);
    }
}
