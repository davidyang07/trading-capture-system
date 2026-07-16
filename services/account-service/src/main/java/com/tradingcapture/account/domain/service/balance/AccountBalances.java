package com.tradingcapture.account.domain.service.balance;

import java.util.List;

/** The full ledger-derived balance picture for an account. */
public record AccountBalances(String accountId,
                              List<CashBalance> cashBalances,
                              List<SecurityPosition> securityPositions) {
}
