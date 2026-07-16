package com.tradingcapture.account.domain.model;

/** Business category of a cash ledger entry (independent of its {@link CashDirection}). */
public enum CashTransactionType implements CodeEnum {
    DEPOSIT,
    WITHDRAWAL,
    TRADE_SETTLEMENT,
    FEE,
    ADJUSTMENT;

    @Override
    public String code() {
        return name();
    }
}
