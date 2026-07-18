package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves external FIX ExecIDs (tag 17) to internal executions and their allocation eligibility,
 * for the allocation-engine (Phase 8). Inbound FIX {@code NoExecs} carries {@code ExecID}, but the
 * allocation-state API binds by internal execution id — this read-only, side-effect-free service
 * closes that gap. Each requested ExecID resolves independently; an unknown id is a per-item
 * {@code NOT_FOUND}, never a batch failure. Only the minimum fields the engine's selection and
 * logical validation need are returned — no full execution disclosure.
 */
@Service
public class ExecutionResolutionService {

    /** Why an ExecID is (in)eligible to be allocated. */
    public enum Reason {
        /** Resolved to an active, unallocated chain tip — allocatable. */
        ELIGIBLE,
        /** No execution has this ExecID. */
        NOT_FOUND,
        /** The execution is a trade cancellation and cannot be allocated. */
        CANCELLED,
        /** A later correction superseded this execution; it is no longer the active tip. */
        SUPERSEDED,
        /** The execution is already allocated (to {@code allocationId}). */
        ALREADY_ALLOCATED
    }

    /** Minimal, deterministic per-item resolution result. */
    public record Resolution(String execId,
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
                             Reason reason) {

        static Resolution notFound(String execId) {
            return new Resolution(execId, false, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, false, Reason.NOT_FOUND);
        }
    }

    private final ExecutionRepository executions;

    public ExecutionResolutionService(ExecutionRepository executions) {
        this.executions = executions;
    }

    @Transactional(readOnly = true)
    public List<Resolution> resolve(Collection<String> execIds) {
        List<String> distinct = execIds.stream().distinct().toList();
        Map<String, Execution> byExecId = executions.findByExecIdIn(distinct).stream()
                .collect(Collectors.toMap(Execution::getExecId, Function.identity(), (a, b) -> a));
        Set<String> superseded = supersededInternalIds(byExecId.values());

        // Preserve the caller's order and multiplicity: each requested ExecID gets one result.
        return execIds.stream()
                .map(execId -> resolveOne(execId, byExecId.get(execId), superseded))
                .toList();
    }

    private Resolution resolveOne(String execId, Execution execution, Set<String> superseded) {
        if (execution == null) {
            return Resolution.notFound(execId);
        }
        Reason reason = classify(execution, superseded);
        boolean eligible = reason == Reason.ELIGIBLE;
        BigDecimal executed = execution.getLastQty();
        return new Resolution(
                execId,
                true,
                execution.getInternalId(),
                execution.getOrder().getOrderId(),
                execution.getOrder().getInstrumentId(),
                execution.getOrder().getAccountId(),
                execution.getSide().code(),
                executed,
                eligible ? executed : BigDecimal.ZERO.setScale(executed.scale()),
                execution.getCurrency(),
                execution.getTradeDate(),
                execution.getSettleType().code(),
                execution.getSettleDate(),
                execution.getTransactTime(),
                execution.getAllocationState().name(),
                execution.getAllocationId(),
                eligible,
                reason);
    }

    private static Reason classify(Execution execution, Set<String> superseded) {
        if (execution.isCancellation()) {
            return Reason.CANCELLED;
        }
        if (superseded.contains(execution.getInternalId())) {
            return Reason.SUPERSEDED;
        }
        if (execution.isAllocated()) {
            return Reason.ALREADY_ALLOCATED;
        }
        return Reason.ELIGIBLE;
    }

    /** Internal ids of the given executions that a later correction/cancellation row references. */
    private Set<String> supersededInternalIds(Collection<Execution> candidates) {
        Set<String> internalIds = candidates.stream()
                .map(Execution::getInternalId)
                .collect(Collectors.toCollection(HashSet::new));
        if (internalIds.isEmpty()) {
            return Set.of();
        }
        return executions.findByExecRefIdIn(internalIds).stream()
                .map(Execution::getExecRefId)
                .filter(internalIds::contains)
                .collect(Collectors.toSet());
    }
}
