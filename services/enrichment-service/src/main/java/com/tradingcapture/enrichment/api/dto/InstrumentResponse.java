package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.InstrumentType;

import java.time.Instant;
import java.util.List;

/** Instrument projection returned by the API (entities are never serialized directly). */
public record InstrumentResponse(String instrumentId,
                                 String name,
                                 InstrumentType instrumentType,
                                 String exchangeMic,
                                 String currencyCode,
                                 ActivationStatus status,
                                 List<AlternateIdentifierResponse> alternateIdentifiers,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Long version) {

    public record AlternateIdentifierResponse(IdentifierSource source, String value, String exchangeMic) {
    }
}
