package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.MiscFeeBasis;

import java.math.BigDecimal;

public record MiscFeeResponse(String feeType,
                              BigDecimal amount,
                              String currency,
                              MiscFeeBasis basis) {
}
