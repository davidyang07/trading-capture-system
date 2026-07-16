package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.service.command.OrderCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Full replacement of the client-editable order fields (PUT); permitted only while NEW. */
public record UpdateOrderRequest(
        @Size(max = 64)
        String clOrdId,

        @NotBlank @Size(max = 64)
        String accountId,

        @NotBlank @Size(max = 64)
        String instrumentId,

        @NotNull
        Side side,

        @NotNull
        OrderType orderType,

        @NotNull @Positive @Digits(integer = 11, fraction = 8)
        BigDecimal price,

        @NotNull
        PriceType priceType,

        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal quantity,

        @NotNull
        SettleType settleType,

        @NotNull
        LocalDate settleDate) {

    public OrderCommands.UpdateOrder toCommand() {
        return new OrderCommands.UpdateOrder(clOrdId, accountId, instrumentId, side, orderType,
                price, priceType, currency, quantity, settleType, settleDate);
    }
}
