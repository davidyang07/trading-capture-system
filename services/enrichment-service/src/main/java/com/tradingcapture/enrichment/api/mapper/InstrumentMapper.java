package com.tradingcapture.enrichment.api.mapper;

import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.api.dto.InstrumentSummaryResponse;
import com.tradingcapture.enrichment.domain.model.Instrument;
import org.springframework.stereotype.Component;

/** Hand-written entity-to-DTO mapping (ADR-0014: no MapStruct, entities never serialized). */
@Component
public class InstrumentMapper {

    /** Full response including alternate identifiers — the instrument must have them loaded. */
    public InstrumentResponse toResponse(Instrument instrument) {
        return new InstrumentResponse(instrument.getInstrumentId(),
                instrument.getName(),
                instrument.getInstrumentType(),
                instrument.getExchangeMic(),
                instrument.getCurrencyCode(),
                instrument.getStatus(),
                instrument.getAlternateIdentifiers().stream()
                        .map(id -> new InstrumentResponse.AlternateIdentifierResponse(
                                id.getSource(), id.getValue(), id.getExchangeMic()))
                        .toList(),
                instrument.getCreatedAt(),
                instrument.getUpdatedAt(),
                instrument.getVersion());
    }

    /** Summary without alternate identifiers — safe to map outside a fetch of the collection. */
    public InstrumentSummaryResponse toSummary(Instrument instrument) {
        return new InstrumentSummaryResponse(instrument.getInstrumentId(),
                instrument.getName(),
                instrument.getInstrumentType(),
                instrument.getExchangeMic(),
                instrument.getCurrencyCode(),
                instrument.getStatus(),
                instrument.getCreatedAt(),
                instrument.getUpdatedAt(),
                instrument.getVersion());
    }
}
