package com.tradingcapture.account.domain.model;

/** Business category of a security ledger entry (independent of its {@link SecurityDirection}). */
public enum SecurityTransactionType implements CodeEnum {
    TRADE_SETTLEMENT,
    TRANSFER_IN,
    TRANSFER_OUT,
    ADJUSTMENT;

    @Override
    public String code() {
        return name();
    }
}
