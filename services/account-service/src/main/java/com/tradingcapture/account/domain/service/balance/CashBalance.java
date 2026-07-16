package com.tradingcapture.account.domain.service.balance;

import java.math.BigDecimal;

/** Ledger-derived cash balance for one currency. */
public record CashBalance(String currency, BigDecimal balance) {
}
