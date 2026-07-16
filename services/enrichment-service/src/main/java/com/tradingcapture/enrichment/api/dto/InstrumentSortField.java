package com.tradingcapture.enrichment.api.dto;

/** Whitelisted instrument sort keys — arbitrary property paths are never accepted. */
public enum InstrumentSortField {
    INSTRUMENT_ID("instrumentId"),
    NAME("name"),
    CREATED_AT("createdAt");

    private final String property;

    InstrumentSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
