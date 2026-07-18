package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.events.OrchestrationEventFactory;
import com.tradingcapture.allocation.outbox.OutboxRecorder;
import com.tradingcapture.allocation.selection.SelectionResult;
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
    private final OrchestrationMetrics metrics;
    private final Clock clock;

    public OrchestrationTx(AllocationRepository allocations, OutboxRecorder outbox,
                           OrchestrationEventFactory events, OrchestrationMetrics metrics, Clock clock) {
        this.allocations = allocations;
        this.outbox = outbox;
        this.events = events;
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

    @Transactional
    public OrchestrationOutcome commitReleased(Long id, String correlationId) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus() != AllocationStatus.VALIDATED) {
            return OrchestrationOutcome.SKIPPED;
        }
        Optional<Allocation> original = allocations.findByAllocIdAndAllocTransType(
                a.getRefAllocId(), AllocTransType.NEW);
        List<String> releasedIds = original.map(o -> o.releaseAllAssignments(now())).orElse(List.of());
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

    @Transactional
    public OrchestrationOutcome commitRejected(Long id, String summary, String stepName) {
        Allocation a = allocations.findById(id).orElseThrow();
        if (a.getStatus().isTerminal()) {
            return OrchestrationOutcome.SKIPPED;
        }
        a.reject(summary, now());
        a.releaseClaim(now());
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
        return step(stepName, OrchestrationOutcome.FAILED);
    }

    private OrchestrationOutcome step(String stepName, OrchestrationOutcome outcome) {
        metrics.step(stepName, outcome);
        return outcome;
    }

    private Instant now() {
        return clock.instant();
    }
}
