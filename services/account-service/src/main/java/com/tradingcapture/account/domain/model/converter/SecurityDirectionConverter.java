package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.SecurityDirection;
import jakarta.persistence.Converter;

@Converter
public class SecurityDirectionConverter extends CodeEnumConverter<SecurityDirection> {

    public SecurityDirectionConverter() {
        super(SecurityDirection.class);
    }
}
