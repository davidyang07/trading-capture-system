package com.tradingcapture.allocation.selection;

import java.math.BigDecimal;
import java.util.List;

/**
 * Input to deterministic execution selection: the requested total quantity, the allocation's
 * instrument and side (candidates must match both to be eligible), and the snapshot of candidates.
 * The candidate collection order does not affect the result — {@link ExecutionSelector} imposes its
 * own total order.
 */
public record SelectionRequest(BigDecimal requestedQuantity,
                               String instrumentId,
                               String side,
                               List<SelectionCandidate> candidates) {
}
