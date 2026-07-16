package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.command.InstrumentCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Instrument creation payload. Status is server-controlled (instruments start ACTIVE). */
public record CreateInstrumentRequest(
        @Schema(example = "AAPL")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "instrumentId must match ^[A-Za-z0-9._-]{1,64}$")
        String instrumentId,

        @NotBlank @Size(max = 255)
        String name,

        @NotNull
        InstrumentType instrumentType,

        @Schema(example = "XNAS")
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{4}$", message = "exchangeMic must be a 4-character MIC")
        String exchangeMic,

        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code")
        String currencyCode,

        @Valid
        List<IdentifierInput> identifiers) {

    public InstrumentCommands.Create toCommand() {
        return new InstrumentCommands.Create(instrumentId, name, instrumentType, exchangeMic, currencyCode,
                identifiers == null ? List.of() : identifiers.stream().map(IdentifierInput::toCommand).toList());
    }
}
