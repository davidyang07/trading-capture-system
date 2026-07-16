package com.tradingcapture.account.domain.model;

/** Direction of a cash ledger entry. A DEBIT reduces the available balance. */
public enum CashDirection implements CodeEnum {
    CREDIT,
    DEBIT;

    @Override
    public String code() {
        return name();
    }
}
