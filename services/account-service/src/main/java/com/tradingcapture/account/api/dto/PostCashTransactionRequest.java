package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.service.command.LedgerCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Cash ledger post. The amount is always strictly positive; {@code direction} decides whether
 * it credits or debits the balance. An {@code idempotencyKey} makes machine-originated posts
 * safe to retry — a replay returns the original row.
 */
public record PostCashTransactionRequest(
        @NotNull
        CashDirection direction,

        @NotNull
        CashTransactionType transactionType,

        @Schema(example = "1000.00")
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @Schema(description = "Optional link to an originating order/execution/allocation")
        @Size(max = 64)
        String externalRef,

        @Schema(description = "Optional idempotency key; a replay with the same key returns the original row")
        @Size(max = 80)
        String idempotencyKey,

        @Size(max = 500)
        String description) {

    public LedgerCommands.PostCash toCommand() {
        return new LedgerCommands.PostCash(direction, transactionType, amount, currency,
                externalRef, idempotencyKey, description);
    }
}
