package com.tradingcapture.enrichment.domain.model;

/**
 * Source system of an alternate security identifier. CUSIP/ISIN/SEDOL are globally unique;
 * an EXCHANGE_SYMBOL is unique only within an exchange (so the same ticker may exist on
 * several exchanges — lookups by symbol must handle that ambiguity).
 */
public enum IdentifierSource implements CodeEnum {
    CUSIP,
    ISIN,
    SEDOL,
    EXCHANGE_SYMBOL;

    /** True when this source is unique per exchange rather than globally. */
    public boolean isExchangeScoped() {
        return this == EXCHANGE_SYMBOL;
    }

    @Override
    public String code() {
        return name();
    }
}
