package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.FeeBasis;
import com.tradingcapture.enrichment.domain.model.FeeType;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.command.FeeRuleCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Fee-rule request/response DTOs. Dimensions (currency/exchange/instrumentType) are optional. */
public final class FeeRuleDtos {

    private FeeRuleDtos() {
    }

    public record FeeRuleResponse(String feeRuleId,
                                  String name,
                                  FeeType feeType,
                                  FeeBasis basis,
                                  BigDecimal rate,
                                  String currencyCode,
                                  String exchangeMic,
                                  InstrumentType instrumentType,
                                  ActivationStatus status,
                                  Instant createdAt,
                                  Instant updatedAt,
                                  Long version) {
    }

    public record CreateFeeRuleRequest(
            @Schema(example = "US-EQ-COMMISSION")
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "feeRuleId must match ^[A-Za-z0-9._-]{1,64}$")
            String feeRuleId,
            @NotBlank @Size(max = 255) String name,
            @NotNull FeeType feeType,
            @NotNull FeeBasis basis,
            @Schema(example = "0.00100000") @NotNull @DecimalMin("0.0") @Digits(integer = 11, fraction = 8) BigDecimal rate,
            @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code") String currencyCode,
            @Pattern(regexp = "^[A-Z0-9]{4}$", message = "exchangeMic must be a 4-character MIC") String exchangeMic,
            InstrumentType instrumentType) {

        public FeeRuleCommands.Create toCommand() {
            return new FeeRuleCommands.Create(feeRuleId, name, feeType, basis, rate, currencyCode, exchangeMic, instrumentType);
        }
    }

    public record UpdateFeeRuleRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull FeeType feeType,
            @NotNull FeeBasis basis,
            @NotNull @DecimalMin("0.0") @Digits(integer = 11, fraction = 8) BigDecimal rate,
            @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code") String currencyCode,
            @Pattern(regexp = "^[A-Z0-9]{4}$", message = "exchangeMic must be a 4-character MIC") String exchangeMic,
            InstrumentType instrumentType) {

        public FeeRuleCommands.Update toCommand() {
            return new FeeRuleCommands.Update(name, feeType, basis, rate, currencyCode, exchangeMic, instrumentType);
        }
    }
}
