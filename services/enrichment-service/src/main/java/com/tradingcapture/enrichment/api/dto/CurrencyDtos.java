package com.tradingcapture.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Currency request/response DTOs. */
public final class CurrencyDtos {

    private CurrencyDtos() {
    }

    public record CurrencyResponse(String code,
                                   String name,
                                   int minorUnits,
                                   Instant createdAt,
                                   Instant updatedAt,
                                   Long version) {
    }

    public record CreateCurrencyRequest(
            @Schema(example = "USD")
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "code must be a 3-letter ISO code")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Schema(example = "2") @NotNull @Min(0) @Max(8) Integer minorUnits) {
    }

    public record UpdateCurrencyRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Min(0) @Max(8) Integer minorUnits) {
    }
}
