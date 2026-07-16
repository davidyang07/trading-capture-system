package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.service.validation.AccountValidationService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Machine batch validation request (allocation-engine / trading-service). Each item is
 * evaluated independently; cash and security requirements are optional per item.
 */
public record AccountBatchValidationRequest(
        @NotEmpty @Size(max = 500) @Valid
        List<Item> items) {

    public record Item(
            @NotBlank @Size(max = 64)
            String accountId,

            @Schema(description = "Currency for the cash-sufficiency check (required with requiredCashAmount)")
            @Pattern(regexp = "^[A-Z]{3}$", message = "requiredCurrency must be a 3-letter ISO code")
            String requiredCurrency,

            @Positive @Digits(integer = 15, fraction = 4)
            BigDecimal requiredCashAmount,

            @Size(max = 64)
            String instrumentId,

            @Positive @Digits(integer = 15, fraction = 4)
            BigDecimal requiredQuantity) {

        public AccountValidationService.Check toCheck() {
            return new AccountValidationService.Check(accountId, requiredCurrency, requiredCashAmount,
                    instrumentId, requiredQuantity);
        }
    }

    public List<AccountValidationService.Check> toChecks() {
        return items.stream().map(Item::toCheck).toList();
    }
}
