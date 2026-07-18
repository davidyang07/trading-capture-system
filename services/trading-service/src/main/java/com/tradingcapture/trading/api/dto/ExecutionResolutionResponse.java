package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.service.ExecutionResolutionService;
import com.tradingcapture.trading.domain.service.ExecutionResolutionService.Resolution;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Per-item resolution verdicts, in the same order (and multiplicity) as the requested ExecIDs.
 * Only fields the allocation-engine's selection and logical validation need are exposed.
 */
public record ExecutionResolutionResponse(List<Item> results) {

    public record Item(String execId,
                       boolean found,
                       String internalId,
                       String orderId,
                       String instrumentId,
                       String accountId,
                       String side,
                       BigDecimal executedQuantity,
                       BigDecimal allocatableQuantity,
                       String currency,
                       LocalDate tradeDate,
                       String settleType,
                       LocalDate settleDate,
                       Instant transactTime,
                       String allocationState,
                       String allocationId,
                       boolean eligible,
                       ExecutionResolutionService.Reason reason) {
    }

    public static ExecutionResolutionResponse from(List<Resolution> resolutions) {
        return new ExecutionResolutionResponse(resolutions.stream()
                .map(r -> new Item(r.execId(), r.found(), r.internalId(), r.orderId(),
                        r.instrumentId(), r.accountId(), r.side(), r.executedQuantity(),
                        r.allocatableQuantity(), r.currency(), r.tradeDate(), r.settleType(),
                        r.settleDate(), r.transactTime(), r.allocationState(), r.allocationId(),
                        r.eligible(), r.reason()))
                .toList());
    }
}
