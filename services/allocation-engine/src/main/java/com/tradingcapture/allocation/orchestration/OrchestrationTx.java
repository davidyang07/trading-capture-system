package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.events.OrchestrationEventFactory;
import com.tradingcapture.allocation.outbox.OutboxRecorder;
import com.tradingcapture.allocation.selection.SelectionResult;
import com.tradingcapture.allocation.validation.logical.LogicalValidationCode;
import com.tradingcapture.allocation.validation.logical.LogicalValidationError;
import com.tradingcapture.events.EventTopics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The atomic, same-transaction mutation + outbox steps of orchestration. Each method reloads the
 * allocation, re-checks its state is still the one this step expects (so a duplicate/stale
 * invocation is a no-op {@code SKIPPED}), mutates state + history + assignments, and records the
 * outbox event(s) in the <em>same</em> transaction — a rollback leaves no partial assignment,
 * transition, or event. Remote calls are performed by {@link AllocationOrchestrator} outside these
 * transactions.
 */
@Component
public class OrchestrationTx {

    private static final String AGGREGATE = "ALLOCATION";

    private final AllocationRepository allocations;
    private final OutboxRecorder outbox;
    private final OrchestrationEventFactory events;
    private final TerminalOutcomeEvents outcomes;
    private final OrchestrationMetrics metrics;
    private final Clock clock;

    public OrchestrationTx(AllocationRepository allocations, OutboxRecorder outbox,
                           OrchestrationEventFactory events, TerminalOutcomeEvents outcomes,
                           OrchestrationMetrics metrics, Clock clock) {
        this.allocations = allocations;
        this.outbox = outbox;
        this.events = events;
        this.outcomes = outcomes;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public OrchestrationOutcome commitValidated(Long id, String correlationId) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus() != AllocationStatus.RECEIVED) {
            return OrchestrationOutcome.SKIPPED;
        }
        a.transitionTo(AllocationStatus.VALIDATED, "logical validation passed", now());
        a.clearTransientFailure(now());
        var event = events.allocationValidated(a, correlationId);
        outbox.record(event.getMetadata(), event, EventTopics.ALLOCATION_VALIDATED, a.getAllocId(), AGGREGATE);
        return step("validate", OrchestrationOutcome.ADVANCED);
    }

    @Transactional
    public OrchestrationOutcome commitAllocated(Long id, List<SelectionResult.SelectedExecution> selections,
                                                String correlationId) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus() != AllocationStatus.VALIDATED
                && a.getStatus() != AllocationStatus.EXECUTIONS_RELEASED) {
            return OrchestrationOutcome.SKIPPED;
        }
        for (SelectionResult.SelectedExecution selection : selections) {
            a.assignExecution(selection.internalId(), selection.selectedQuantity(), now());
        }
        a.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, "executions allocated", now());
        a.clearTransientFailure(now());
        var event = events.executionsAllocated(a, correlationId);
        outbox.record(event.getMetadata(), event, EventTopics.EXECUTIONS_ALLOCATED, a.getAllocId(), AGGREGATE);
        return step("allocate", OrchestrationOutcome.ADVANCED);
    }

    /**
     * Commits a Replace/Cancel's release and retires the original it references
     * ({@code -> REPLACED / CANCELLED}) in one transaction. If the original is no longer live — a
     * concurrent Replace/Cancel of it committed first — this instruction is rejected instead; the
     * original's {@code @Version} makes two successors racing here fail one of them.
     */
    @Transactional
    public OrchestrationOutcome commitReleased(Long id, String correlationId) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus() != AllocationStatus.VALIDATED) {
            return OrchestrationOutcome.SKIPPED;
        }
        Optional<Allocation> original = allocations.findReferencedOriginal(a.getRefAllocId(), a.getId())
                .filter(o -> o.getStatus().isSupersedable());
        if (original.isEmpty()) {
            String summary = "original allocation " + a.getRefAllocId() + " is no longer live";
            a.reject(summary, now());
            a.releaseClaim(now());
            LogicalValidationCode code = a.getAllocTransType() == AllocTransType.CANCEL
                    ? LogicalValidationCode.CANCEL_ORIGINAL_INELIGIBLE
                    : LogicalValidationCode.REPLACE_ORIGINAL_INELIGIBLE;
            outcomes.rejected(a, List.of(new LogicalValidationError(code, a.getRefAllocId(), summary)));
            return step("release", OrchestrationOutcome.REJECTED);
        }
        List<String> releasedIds = original.get().releaseAllAssignments(now());
        original.get().markSupersededBy(a, now());
        a.transitionTo(AllocationStatus.EXECUTIONS_RELEASED, "executions released", now());
        a.clearTransientFailure(now());
        var event = events.executionsReleased(a, a.getRefAllocId(), releasedIds, correlationId);
        outbox.record(event.getMetadata(), event, EventTopics.EXECUTIONS_RELEASED, a.getAllocId(), AGGREGATE);
        return step("release", OrchestrationOutcome.ADVANCED);
    }

    @Transactional
    public OrchestrationOutcome commitCompleted(Long id, String correlationId, boolean emitSettlement) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus() != AllocationStatus.EXECUTIONS_ALLOCATED
                && a.getStatus() != AllocationStatus.EXECUTIONS_RELEASED) {
            return OrchestrationOutcome.SKIPPED;
        }
        a.markCompleted(now());
        a.releaseClaim(now());
        if (emitSettlement) {
            var event = events.settlementRequested(a, correlationId);
            outbox.record(event.getMetadata(), event, EventTopics.SETTLEMENT_REQUESTED, a.getAllocId(), AGGREGATE);
        }
        return step("complete", OrchestrationOutcome.COMPLETED);
    }

    /** Business rejection: REJECTED, announced with every reason in {@code errors}. */
    @Transactional
    public OrchestrationOutcome commitRejected(Long id, String summary, List<LogicalValidationError> errors,
                                               String stepName) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus().isTerminal()) {
            return OrchestrationOutcome.SKIPPED;
        }
        a.reject(summary, now());
        a.releaseClaim(now());
        outcomes.rejected(a, errors);
        return step(stepName, OrchestrationOutcome.REJECTED);
    }

    @Transactional
    public OrchestrationOutcome commitRetryOrFail(Long id, OrchestrationFailureCategory category,
                                                  String summary, Instant nextAttemptAt,
                                                  boolean exhausted, String stepName) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus().isTerminal()) {
            return OrchestrationOutcome.SKIPPED;
        }
        if (exhausted) {
            a.failTerminally(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION,
                    "retries exhausted: " + summary, now());
            a.releaseClaim(now());
            outcomes.failed(a);
            return step(stepName, OrchestrationOutcome.FAILED);
        }
        a.recordRetryableFailure(category, summary, nextAttemptAt, now());
        a.releaseClaim(now());
        return step(stepName, OrchestrationOutcome.RETRY_SCHEDULED);
    }

    @Transactional
    public OrchestrationOutcome commitFailed(Long id, OrchestrationFailureCategory category,
                                             String summary, String stepName) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus().isTerminal()) {
            return OrchestrationOutcome.SKIPPED;
        }
        a.failTerminally(category, summary, now());
        a.releaseClaim(now());
        outcomes.failed(a);
        return step(stepName, OrchestrationOutcome.FAILED);
    }

    /**
     * trading-service released everything bound to a failed or rejected allocation's allocId:
     * clear the flag and release its own assignments. When an earlier ExecutionsAllocated announced
     * executions, announce their release (allocId = refAllocId: the allocation released its own).
     */
    @Transactional
    public boolean commitExecutionRelease(Long id) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (!a.isExecutionsReleasePending()) {
            return false;
        }
        List<String> releasedIds = a.completeExecutionRelease(now());
        if (!releasedIds.isEmpty()) {
            var event = events.executionsReleased(a, a.getAllocId(), releasedIds, outcomes.correlationId(a));
            outbox.record(event.getMetadata(), event, EventTopics.EXECUTIONS_RELEASED, a.getAllocId(), AGGREGATE);
        }
        return true;
    }

    @Transactional
    public void recordExecutionReleaseFailure(Long id, String error, Instant nextReleaseAt) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.isExecutionsReleasePending()) {
            a.recordReleaseFailure(error, nextReleaseAt, now());
        }
    }

    private OrchestrationOutcome step(String stepName, OrchestrationOutcome outcome) {
        metrics.step(stepName, outcome);
        return outcome;
    }

    private Instant now() {
        return clock.instant();
    }
}
