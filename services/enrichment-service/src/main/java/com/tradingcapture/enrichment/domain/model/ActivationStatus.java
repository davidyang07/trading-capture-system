package com.tradingcapture.enrichment.domain.model;

/** Whether a reference-data record is available for use. */
public enum ActivationStatus implements CodeEnum {
    ACTIVE,
    INACTIVE;

    @Override
    public String code() {
        return name();
    }
}
