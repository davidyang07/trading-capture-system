package com.tradingcapture.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Settlement-option request/response DTOs. */
public final class SettlementOptionDtos {

    private SettlementOptionDtos() {
    }

    public record SettlementOptionResponse(String code,
                                           String name,
                                           int settlementDays,
                                           String description,
                                           Instant createdAt,
                                           Instant updatedAt,
                                           Long version) {
    }

    public record CreateSettlementOptionRequest(
            @Schema(example = "T_PLUS_2")
            @NotBlank @Pattern(regexp = "^[A-Z0-9_]{1,16}$", message = "code must match ^[A-Z0-9_]{1,16}$")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Schema(example = "2") @NotNull @Min(0) @Max(30) Integer settlementDays,
            @Size(max = 500) String description) {
    }

    public record UpdateSettlementOptionRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Min(0) @Max(30) Integer settlementDays,
            @Size(max = 500) String description) {
    }
}
