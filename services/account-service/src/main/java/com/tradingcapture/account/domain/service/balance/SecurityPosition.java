package com.tradingcapture.account.domain.service.balance;

import java.math.BigDecimal;

/** Ledger-derived security position for one instrument. */
public record SecurityPosition(String instrumentId, BigDecimal quantity) {
}
