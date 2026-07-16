package com.tradingcapture.enrichment.domain.service.command;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.InstrumentType;

import java.util.List;

/** Service-layer commands for instrument administration, decoupled from the API DTOs. */
public final class InstrumentCommands {

    private InstrumentCommands() {
    }

    public record Identifier(IdentifierSource source, String value) {
    }

    public record Create(String instrumentId,
                         String name,
                         InstrumentType instrumentType,
                         String exchangeMic,
                         String currencyCode,
                         List<Identifier> identifiers) {
    }

    public record Update(String name,
                         InstrumentType instrumentType,
                         String exchangeMic,
                         String currencyCode,
                         List<Identifier> identifiers) {
    }
}
