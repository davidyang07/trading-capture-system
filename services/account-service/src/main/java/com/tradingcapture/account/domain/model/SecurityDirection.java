package com.tradingcapture.account.domain.model;

/** Direction of a security ledger entry. A DELIVERY reduces the held position. */
public enum SecurityDirection implements CodeEnum {
    RECEIPT,
    DELIVERY;

    @Override
    public String code() {
        return name();
    }
}
