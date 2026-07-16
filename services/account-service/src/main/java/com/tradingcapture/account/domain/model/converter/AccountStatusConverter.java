package com.tradingcapture.account.domain.model.converter;

import com.tradingcapture.account.domain.model.AccountStatus;
import jakarta.persistence.Converter;

@Converter
public class AccountStatusConverter extends CodeEnumConverter<AccountStatus> {

    public AccountStatusConverter() {
        super(AccountStatus.class);
    }
}
