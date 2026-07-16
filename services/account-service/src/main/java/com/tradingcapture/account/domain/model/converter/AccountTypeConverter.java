package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.AccountType;
import jakarta.persistence.Converter;

@Converter
public class AccountTypeConverter extends CodeEnumConverter<AccountType> {

    public AccountTypeConverter() {
        super(AccountType.class);
    }
}
