package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.command.InstrumentCommands;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Instrument update payload (replaces mutable fields and the identifier set). */
public record UpdateInstrumentRequest(
        @NotBlank @Size(max = 255)
        String name,

        @NotNull
        InstrumentType instrumentType,

        @NotBlank @Pattern(regexp = "^[A-Z0-9]{4}$", message = "exchangeMic must be a 4-character MIC")
        String exchangeMic,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code")
        String currencyCode,

        @Valid
        List<IdentifierInput> identifiers) {

    public InstrumentCommands.Update toCommand() {
        return new InstrumentCommands.Update(name, instrumentType, exchangeMic, currencyCode,
                identifiers == null ? List.of() : identifiers.stream().map(IdentifierInput::toCommand).toList());
    }
}
