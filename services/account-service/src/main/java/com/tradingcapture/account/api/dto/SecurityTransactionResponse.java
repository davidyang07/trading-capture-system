package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/** An immutable security ledger row as returned by the API. */
public record SecurityTransactionResponse(Long id,
                                          String transactionRef,
                                          String accountId,
                                          SecurityDirection direction,
                                          SecurityTransactionType transactionType,
                                          String instrumentId,
                                          BigDecimal quantity,
                                          String externalRef,
                                          String idempotencyKey,
                                          String description,
                                          Instant postedAt) {
}
