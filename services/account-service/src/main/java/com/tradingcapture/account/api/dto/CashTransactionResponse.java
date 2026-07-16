package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/** An immutable cash ledger row as returned by the API. */
public record CashTransactionResponse(Long id,
                                      String transactionRef,
                                      String accountId,
                                      CashDirection direction,
                                      CashTransactionType transactionType,
                                      BigDecimal amount,
                                      String currency,
                                      String externalRef,
                                      String idempotencyKey,
                                      String description,
                                      Instant postedAt) {
}
