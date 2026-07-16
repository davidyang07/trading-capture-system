package com.tradingcapture.enrichment.domain.model;

/** Asset class of an instrument. Persisted as its own name via {@link CodeEnum}. */
public enum InstrumentType implements CodeEnum {
    EQUITY,
    BOND,
    ETF,
    FUND;

    @Override
    public String code() {
        return name();
    }
}
