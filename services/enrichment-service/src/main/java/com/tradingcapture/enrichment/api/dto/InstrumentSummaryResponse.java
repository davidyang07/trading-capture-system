package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.InstrumentType;

import java.time.Instant;

/**
 * Instrument projection without alternate identifiers — used by search pages and machine batch
 * resolution, where the caller does not need (and often already knows) the identifiers. Avoids a
 * lazy-collection load per row.
 */
public record InstrumentSummaryResponse(String instrumentId,
                                        String name,
                                        InstrumentType instrumentType,
                                        String exchangeMic,
                                        String currencyCode,
                                        ActivationStatus status,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        Long version) {
}
