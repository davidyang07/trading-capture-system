package com.tradingcapture.enrichment.domain.model;

/** How a fee rule's rate is applied to a trade. */
public enum FeeBasis implements CodeEnum {
    /** A flat amount in the rule's currency. */
    ABSOLUTE,
    /** Rate multiplied by the traded quantity. */
    PER_UNIT,
    /** Rate as a fraction of notional (e.g. 0.0010 = 10 bps). */
    PERCENTAGE;

    @Override
    public String code() {
        return name();
    }
}
