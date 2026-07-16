package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.service.InstrumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Machine batch instrument-resolution request (allocation-engine / trading-service). Each item
 * resolves an alternate identifier; {@code exchangeMic} disambiguates an exchange symbol.
 */
public record InstrumentBatchLookupRequest(
        @NotEmpty @Size(max = 500) @Valid
        List<Item> items) {

    public record Item(
            @NotNull IdentifierSource source,
            @NotBlank @Size(max = 64) String value,
            @Pattern(regexp = "^[A-Z0-9]{4}$", message = "exchangeMic must be a 4-character MIC") String exchangeMic) {

        public InstrumentService.LookupKey toKey() {
            return new InstrumentService.LookupKey(source, value, exchangeMic);
        }
    }

    public List<InstrumentService.LookupKey> toKeys() {
        return items.stream().map(Item::toKey).toList();
    }
}
