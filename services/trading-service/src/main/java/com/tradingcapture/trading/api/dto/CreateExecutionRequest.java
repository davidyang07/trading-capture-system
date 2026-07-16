package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Trade capture payload. cumQty, leavesQty, avgPx and the resulting order status are derived
 * by the server; any client-side notion of them is ignored by design.
 */
public record CreateExecutionRequest(
        @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "External execution ID (FIX ExecID); generated when omitted")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "execId must match ^[A-Za-z0-9._-]{1,64}$")
        String execId,

        @Schema(description = "Fill quantity", example = "100")
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal lastQty,

        @Schema(description = "Fill price", example = "189.20")
        @NotNull @Positive @Digits(integer = 11, fraction = 8)
        BigDecimal lastPx,

        @Schema(description = "Defaults to the current UTC date")
        LocalDate tradeDate,

        @Schema(description = "Defaults to the current instant (UTC)")
        Instant transactTime,

        @Size(max = 500)
        String text,

        @Valid
        List<MiscFeeRequest> miscFees) {

    public ExecutionCommands.CreateTrade toCommand() {
        return new ExecutionCommands.CreateTrade(orderId, execId, lastQty, lastPx, tradeDate,
                transactTime, text,
                miscFees == null ? null : miscFees.stream().map(MiscFeeRequest::toCommand).toList());
    }
}
