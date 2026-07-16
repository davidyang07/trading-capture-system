package com.tradingcapture.account.domain.model;

/** Kind of account. Persisted as its own name via {@link CodeEnum}. */
public enum AccountType implements CodeEnum {
    CASH,
    MARGIN;

    @Override
    public String code() {
        return name();
    }
}
