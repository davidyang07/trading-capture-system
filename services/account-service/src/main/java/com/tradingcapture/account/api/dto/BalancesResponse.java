package com.tradingcapture.account.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ledger-derived balances for an account: cash by currency, positions by instrument. */
public record BalancesResponse(String accountId,
                               List<CashBalanceResponse> cashBalances,
                               List<SecurityPositionResponse> securityPositions) {

    public record CashBalanceResponse(String currency, BigDecimal balance) {
    }

    public record SecurityPositionResponse(String instrumentId, BigDecimal quantity) {
    }
}
