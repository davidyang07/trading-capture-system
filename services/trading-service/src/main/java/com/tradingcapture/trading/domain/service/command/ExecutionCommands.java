package com.tradingcapture.trading.domain.service.command;

import com.tradingcapture.trading.domain.model.MiscFeeBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Typed commands for execution capture and amendment. Note what is <em>not</em> here:
 * cumulative quantity, leaves quantity, average price, and order status are derived by the
 * service and can never be supplied by a client.
 */
public final class ExecutionCommands {

    private ExecutionCommands() {
    }

    public record MiscFee(String feeType, BigDecimal amount, String currency, MiscFeeBasis basis) {
    }

    /**
     * @param execId       optional external execution ID; generated when null
     * @param tradeDate    optional; defaults to the current UTC date
     * @param transactTime optional; defaults to the current instant
     */
    public record CreateTrade(String orderId,
                              String execId,
                              BigDecimal lastQty,
                              BigDecimal lastPx,
                              LocalDate tradeDate,
                              Instant transactTime,
                              String text,
                              List<MiscFee> miscFees) {
    }

    /** @param miscFees null = inherit the target's fees; empty list = correction carries none */
    public record Correct(String execId,
                          BigDecimal lastQty,
                          BigDecimal lastPx,
                          String text,
                          List<MiscFee> miscFees) {
    }

    public record Cancel(String execId, String text) {
    }
}
