package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.ExecutionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Execution detail: the record itself, its fees, and its full correction/cancellation chain.
 *
 * @param chainActive false when the chain's tip is a cancellation (the trade is void)
 */
public record ExecutionDetailResponse(ExecutionResponse execution,
                                      List<MiscFeeResponse> miscFees,
                                      String chainRootExecutionId,
                                      boolean chainActive,
                                      List<ChainLink> chain) {

    /** One link of the chain in write order (root first, tip last). */
    public record ChainLink(String internalId,
                            String execId,
                            ExecutionType execType,
                            BigDecimal lastQty,
                            BigDecimal lastPx,
                            Instant transactTime,
                            boolean tip) {
    }
}
