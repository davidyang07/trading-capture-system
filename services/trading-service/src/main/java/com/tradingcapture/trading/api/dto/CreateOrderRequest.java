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

/**
 * Order creation payload. Deliberately absent: owner/subject (derived from the JWT),
 * status, cumQty, leavesQty (always server-derived).
 */
public record CreateOrderRequest(
        @Schema(description = "Client-supplied business order ID; generated when omitted", example = "ORD-2026-0001")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "orderId must match ^[A-Za-z0-9._-]{1,64}$")
        String orderId,

        @Schema(description = "Client order reference (FIX ClOrdID)", example = "clord-7731")
        @Size(max = 64)
        String clOrdId,

        @Schema(description = "Trading account identifier (validated against account-service from Phase 4)", example = "ACC-SEED-INV1")
        @NotBlank @Size(max = 64)
        String accountId,

        @Schema(description = "Instrument identifier (validated against enrichment-service from Phase 5)", example = "AAPL")
        @NotBlank @Size(max = 64)
        String instrumentId,

        @NotNull
        Side side,

        @NotNull
        OrderType orderType,

        @Schema(example = "189.25")
        @NotNull @Positive @Digits(integer = 11, fraction = 8)
        BigDecimal price,

        @NotNull
        PriceType priceType,

        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @Schema(example = "100")
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal quantity,

        @NotNull
        SettleType settleType,

        @Schema(example = "2026-07-03")
        @NotNull
        LocalDate settleDate) {

    public OrderCommands.CreateOrder toCommand() {
        return new OrderCommands.CreateOrder(orderId, clOrdId, accountId, instrumentId, side,
                orderType, price, priceType, currency, quantity, settleType, settleDate);
    }
}
