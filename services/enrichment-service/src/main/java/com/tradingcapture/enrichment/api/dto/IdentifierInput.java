package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.service.command.InstrumentCommands;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An alternate identifier supplied on an instrument create/update (value normalized server-side). */
public record IdentifierInput(
        @NotNull IdentifierSource source,
        @NotBlank @Size(max = 64) String value) {

    public InstrumentCommands.Identifier toCommand() {
        return new InstrumentCommands.Identifier(source, value);
    }
}
