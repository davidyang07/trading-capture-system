package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Correction payload: restates the trade's quantity/price on a new TRADE_CORRECT record.
 * {@code miscFees} semantics: omitted/null = the correction keeps the target's fees;
 * empty array = the correction carries no fees; non-empty = replaces the fee set.
 */
public record CorrectExecutionRequest(
        @Schema(description = "External execution ID for the correction record; generated when omitted")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "execId must match ^[A-Za-z0-9._-]{1,64}$")
        String execId,

        @Schema(description = "Restated fill quantity", example = "150")
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal lastQty,

        @Schema(description = "Restated fill price", example = "241.00")
        @NotNull @Positive @Digits(integer = 11, fraction = 8)
        BigDecimal lastPx,

        @Size(max = 500)
        String text,

        @Valid
        List<MiscFeeRequest> miscFees) {

    public ExecutionCommands.Correct toCommand() {
        return new ExecutionCommands.Correct(execId, lastQty, lastPx, text,
                miscFees == null ? null : miscFees.stream().map(MiscFeeRequest::toCommand).toList());
    }
}
