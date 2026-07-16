package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.MiscFeeBasis;
import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MiscFeeRequest(
        @NotBlank @Size(max = 20)
        String feeType,

        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @NotNull
        MiscFeeBasis basis) {

    public ExecutionCommands.MiscFee toCommand() {
        return new ExecutionCommands.MiscFee(feeType, amount, currency, basis);
    }
}
