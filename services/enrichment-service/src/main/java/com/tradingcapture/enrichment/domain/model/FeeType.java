package com.tradingcapture.enrichment.domain.model;

/** Category of a miscellaneous fee rule. */
public enum FeeType implements CodeEnum {
    COMMISSION,
    EXCHANGE_FEE,
    REGULATORY_FEE,
    CLEARING_FEE,
    TAX;

    @Override
    public String code() {
        return name();
    }
}
