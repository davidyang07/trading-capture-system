package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.InstrumentType;

/** Filter inputs for the instrument search endpoint. */
public record InstrumentSearchCriteria(InstrumentType instrumentType,
                                       String exchangeMic,
                                       String currency,
                                       ActivationStatus status) {
}
