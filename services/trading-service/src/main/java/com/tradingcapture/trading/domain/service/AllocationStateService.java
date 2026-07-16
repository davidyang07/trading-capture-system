package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;
import com.tradingcapture.trading.observability.TradingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Machine-facing allocation-state transitions (scope {@code allocation:process}), consumed
 * by the allocation-engine from Phase 8.
 *
 * <p>Semantics required by ADR-0006/ADR-0007 (engine steps must be safely re-runnable):</p>
 * <ul>
 *   <li>ALLOCATE binds active executions to one allocationId. Re-applying the same
 *       allocationId is a no-op success; a different allocationId on an ALLOCATED execution
 *       is a 409 conflict.</li>
 *   <li>Only active chain tips can be allocated — cancelled or superseded records are
 *       ineligible.</li>
 *   <li>RELEASE unbinds; with explicit IDs it verifies they belong to the allocationId,
 *       without IDs it releases everything bound to it. Releasing something not (any
 *       longer) bound is a no-op success.</li>
 *   <li>Rows carry {@code @Version}, so concurrent state flips cannot silently collide.</li>
 * </ul>
 */
@Service
public class AllocationStateService {

    private static final Logger log = LoggerFactory.getLogger(AllocationStateService.class);

    /** Post-transition snapshot returned to the machine caller. */
    public record Result(String allocationId,
                         AllocationStateCommand.Action action,
                         int appliedCount,
                         List<Execution> executions) {
    }

    private final ExecutionRepository executions;
    private final TradingMetrics metrics;
    private final Clock clock;

    public AllocationStateService(ExecutionRepository executions, TradingMetrics metrics, Clock clock) {
        this.executions = executions;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Result apply(AllocationStateCommand command) {
        Instant now = Instant.now(clock);
        Result result = switch (command.action()) {
            case ALLOCATE -> allocate(command, now);
            case RELEASE -> release(command, now);
        };
        metrics.allocationStateApplied(command.action().name(), result.appliedCount());
        log.info("Allocation state applied: allocationId={} action={} targeted={} transitioned={}",
                command.allocationId(), command.action(), result.executions().size(), result.appliedCount());
        return result;
    }

    private Result allocate(AllocationStateCommand command, Instant now) {
        List<Execution> targets = loadAll(command.executionInternalIds());
        Set<String> superseded = supersededIds(command.executionInternalIds());
        int applied = 0;
        for (Execution execution : targets) {
            if (execution.isCancellation() || superseded.contains(execution.getInternalId())) {
                throw new BusinessRuleException(ErrorCode.ALLOCATION_TARGET_INELIGIBLE,
                        "Execution " + execution.getInternalId()
                                + " is cancelled or superseded and cannot be allocated");
            }
            if (!execution.isAllocated()) {
                execution.allocate(command.allocationId(), now);
                applied++;
            } else if (!command.allocationId().equals(execution.getAllocationId())) {
                throw new BusinessRuleException(ErrorCode.ALLOCATION_STATE_CONFLICT,
                        "Execution " + execution.getInternalId() + " is already allocated to "
                                + execution.getAllocationId());
            }
            // already allocated to the same allocationId -> idempotent no-op
        }
        return new Result(command.allocationId(), command.action(), applied, targets);
    }

    private Result release(AllocationStateCommand command, Instant now) {
        List<Execution> targets = command.executionInternalIds() != null
                ? loadAll(command.executionInternalIds())
                : executions.findByAllocationId(command.allocationId());
        int applied = 0;
        for (Execution execution : targets) {
            if (!execution.isAllocated()) {
                continue; // idempotent no-op
            }
            if (!command.allocationId().equals(execution.getAllocationId())) {
                throw new BusinessRuleException(ErrorCode.ALLOCATION_STATE_CONFLICT,
                        "Execution " + execution.getInternalId() + " is allocated to "
                                + execution.getAllocationId() + ", not " + command.allocationId());
            }
            execution.releaseAllocation(now);
            applied++;
        }
        return new Result(command.allocationId(), command.action(), applied, targets);
    }

    private List<Execution> loadAll(List<String> internalIds) {
        List<Execution> found = executions.findAllById(internalIds);
        if (found.size() != new HashSet<>(internalIds).size()) {
            List<String> missing = new ArrayList<>(internalIds);
            found.forEach(execution -> missing.remove(execution.getInternalId()));
            throw ResourceNotFoundException.execution(String.join(", ", missing));
        }
        return found;
    }

    private Set<String> supersededIds(List<String> internalIds) {
        Set<String> superseded = new HashSet<>();
        for (Execution amendment : executions.findByExecRefIdIn(internalIds)) {
            superseded.add(amendment.getExecRefId());
        }
        return superseded;
    }
}
