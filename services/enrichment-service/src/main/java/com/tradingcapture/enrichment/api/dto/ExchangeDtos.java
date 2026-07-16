package com.tradingcapture.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Exchange request/response DTOs. */
public final class ExchangeDtos {

    private ExchangeDtos() {
    }

    public record ExchangeResponse(String mic,
                                   String name,
                                   String country,
                                   String timezone,
                                   Instant createdAt,
                                   Instant updatedAt,
                                   Long version) {
    }

    public record CreateExchangeRequest(
            @Schema(example = "XNAS")
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{4}$", message = "mic must be a 4-character MIC")
            String mic,
            @NotBlank @Size(max = 255) String name,
            @Schema(example = "US") @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "country must be a 2-letter ISO code")
            String country,
            @Schema(example = "America/New_York") @NotBlank @Size(max = 64) String timezone) {
    }

    public record UpdateExchangeRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "country must be a 2-letter ISO code") String country,
            @NotBlank @Size(max = 64) String timezone) {
    }
}
