package com.tradingcapture.allocation.selection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A framework-free projection of one resolved execution offered to {@link ExecutionSelector}. Built
 * by the orchestration driver from a {@code TradingExecutionClient.ExecutionResolution} (never a JPA
 * entity or HTTP type). {@code allocatableQuantity} is the execution's remaining unallocated quantity
 * at the snapshot; {@code transactTime} is the deterministic FIFO ordering field.
 */
public record SelectionCandidate(String internalId,
                                 String execId,
                                 String instrumentId,
                                 String side,
                                 BigDecimal allocatableQuantity,
                                 Instant transactTime,
                                 boolean eligible) {
}
