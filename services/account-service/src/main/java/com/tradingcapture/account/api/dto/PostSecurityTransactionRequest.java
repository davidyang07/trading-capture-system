package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransactionType;
import com.tradingcapture.account.domain.service.command.LedgerCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Security ledger post. The quantity is always strictly positive; {@code direction} decides
 * whether it is a RECEIPT (increases the position) or a DELIVERY (decreases it).
 */
public record PostSecurityTransactionRequest(
        @NotNull
        SecurityDirection direction,

        @NotNull
        SecurityTransactionType transactionType,

        @Schema(example = "AAPL")
        @NotBlank @Size(max = 64)
        String instrumentId,

        @Schema(example = "100")
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal quantity,

        @Schema(description = "Optional link to an originating order/execution/allocation")
        @Size(max = 64)
        String externalRef,

        @Schema(description = "Optional idempotency key; a replay with the same key returns the original row")
        @Size(max = 80)
        String idempotencyKey,

        @Size(max = 500)
        String description) {

    public LedgerCommands.PostSecurity toCommand() {
        return new LedgerCommands.PostSecurity(direction, transactionType, instrumentId, quantity,
                externalRef, idempotencyKey, description);
    }
}
