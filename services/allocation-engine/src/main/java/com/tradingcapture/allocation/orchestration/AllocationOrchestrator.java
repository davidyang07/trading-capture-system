package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.client.AccountValidationClient;
import com.tradingcapture.allocation.client.DependencyCallException;
import com.tradingcapture.allocation.client.InstrumentResolutionClient;
import com.tradingcapture.allocation.client.TradingExecutionClient;
import com.tradingcapture.allocation.client.TradingExecutionClient.ExecutionResolution;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.IllegalAllocationTransitionException;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import com.tradingcapture.allocation.orchestration.OrchestrationReader.Snapshot;
import com.tradingcapture.allocation.selection.ExecutionSelector;
import com.tradingcapture.allocation.selection.SelectionCandidate;
import com.tradingcapture.allocation.selection.SelectionRequest;
import com.tradingcapture.allocation.selection.SelectionResult;
import com.tradingcapture.allocation.validation.logical.LogicalAllocationValidator;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.ExistingAllocationView;
import com.tradingcapture.allocation.validation.logical.LogicalValidationError;
import com.tradingcapture.allocation.validation.logical.LogicalValidationResult;
import com.tradingcapture.observability.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drives one allocation through the ADR-0006 lifecycle as a sequence of idempotent, state-guarded
 * steps. Each step first reads an immutable {@link Snapshot} (in a short read transaction), then
 * performs the account/enrichment/trading calls <em>outside</em> any mutating transaction, then
 * commits an atomic mutation + outbox via {@link OrchestrationTx}. Duplicate delivery, restart, and
 * recovery resume from persisted state without repeating side effects (the trading allocation-state
 * API is idempotent, keyed by allocId).
 */
@Component
public class AllocationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AllocationOrchestrator.class);

    private final OrchestrationReader reader;
    private final RawMessageRepository rawMessages;
    private final AccountValidationClient accountClient;
    private final InstrumentResolutionClient instrumentClient;
    private final TradingExecutionClient tradingClient;
    private final LogicalAllocationValidator validator;
    private final ExecutionSelector selector;
    private final OrchestrationTx tx;
    private final AllocationDedupFilter dedupFilter;
    private final OrchestrationProperties properties;
    private final OrchestrationMetrics metrics;
    private final Clock clock;

    public AllocationOrchestrator(OrchestrationReader reader, RawMessageRepository rawMessages,
                                  AccountValidationClient accountClient, InstrumentResolutionClient instrumentClient,
                                  TradingExecutionClient tradingClient, LogicalAllocationValidator validator,
                                  ExecutionSelector selector, OrchestrationTx tx, AllocationDedupFilter dedupFilter,
                                  OrchestrationProperties properties, OrchestrationMetrics metrics, Clock clock) {
        this.reader = reader;
        this.rawMessages = rawMessages;
        this.accountClient = accountClient;
        this.instrumentClient = instrumentClient;
        this.tradingClient = tradingClient;
        this.validator = validator;
        this.selector = selector;
        this.tx = tx;
        this.dedupFilter = dedupFilter;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Advance one claimed allocation as far as it will go this pass (each step commits atomically). */
    public OrchestrationOutcome process(Long allocationId) {
        Snapshot initial = reader.load(allocationId).orElse(null);
        if (initial == null) {
            return OrchestrationOutcome.SKIPPED;
        }
        // Redis dedup fast-path (ADR-0007): a hit is only a hint — the freshly-loaded snapshot is the
        // authoritative confirmation, and the filter fails open, so a false positive never drops work.
        if (dedupFilter.isLikelyProcessed(businessKey(initial)) && initial.status().isTerminal()) {
            return OrchestrationOutcome.SKIPPED;
        }
        String correlationId = resolveCorrelationId(initial.rawMessageId());
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            OrchestrationOutcome outcome = OrchestrationOutcome.ADVANCED;
            for (int step = 0; step < properties.getMaxStepsPerRun() && outcome == OrchestrationOutcome.ADVANCED; step++) {
                Snapshot current = reader.load(allocationId).orElse(null);
                if (current == null || current.status().isTerminal()) {
                    break;
                }
                outcome = advance(current, correlationId);
            }
            return outcome;
        } catch (OptimisticLockingFailureException e) {
            metrics.driver("conflict");
            log.info("Orchestration concurrency conflict for allocationId={} (will be retried)", allocationId);
            return OrchestrationOutcome.RETRY_SCHEDULED;
        } catch (IllegalAllocationTransitionException e) {
            // Defense in depth: an unexpected illegal transition must not leave the row stuck
            // non-terminal (retried forever). FAILED is legal from every working state.
            metrics.driver("conflict");
            log.error("Illegal transition during orchestration for allocationId={}: {} — escalating to FAILED",
                    allocationId, e.getMessage());
            return tx.commitFailed(allocationId, OrchestrationFailureCategory.INVARIANT_VIOLATION,
                    "illegal state transition", "orchestrate");
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private OrchestrationOutcome advance(Snapshot a, String correlationId) {
        return switch (a.status()) {
            case RECEIVED -> validateStep(a, correlationId);
            case VALIDATED -> a.transType() == AllocTransType.NEW
                    ? allocateStep(a, correlationId)
                    : releaseStep(a, correlationId);
            case EXECUTIONS_RELEASED -> a.transType() == AllocTransType.REPLACE
                    ? allocateStep(a, correlationId)
                    : completeStep(a, correlationId);
            case EXECUTIONS_ALLOCATED -> completeStep(a, correlationId);
            default -> OrchestrationOutcome.SKIPPED;
        };
    }

    // --- steps ---------------------------------------------------------------------------------

    private OrchestrationOutcome validateStep(Snapshot a, String correlationId) {
        AllocationView view = a.view();
        List<AccountValidationClient.AccountValidation> accountResults = List.of();
        List<InstrumentResolutionClient.InstrumentResolution> instrumentResults = List.of();
        List<ExecutionResolution> executionResults = List.of();
        // A Cancel allocates nothing — it only releases the allocation it references — so it is
        // validated against that original alone and needs no account/instrument/execution lookups.
        if (a.transType() != AllocTransType.CANCEL) {
            try {
                accountResults = accountClient.validate(accountChecks(view));
                instrumentResults = instrumentClient.resolve(List.of(instrumentKey(view)));
                executionResults = tradingClient.resolveExecutions(view.execIds());
            } catch (DependencyCallException e) {
                return handleDependencyFailure(a, e, "validate");
            }
        }

        ExistingAllocationView existing = a.transType() == AllocTransType.NEW
                ? null
                : reader.existingOriginal(a.refAllocId(), a.id()).orElse(null);

        LogicalValidationContext context = new LogicalValidationContext(view, accountResults,
                instrumentResults, executionResults, null, existing);
        LogicalValidationResult result = validator.validate(context);

        if (result.isValid()) {
            return tx.commitValidated(a.id(), correlationId);
        }
        if (result instanceof LogicalValidationResult.BusinessRejected rejected) {
            return tx.commitRejected(a.id(), summarize(rejected.errors()), "validate");
        }
        if (result instanceof LogicalValidationResult.DependencyUnavailable dep) {
            return scheduleRetry(a, dep.category(), dep.summary(), "validate");
        }
        if (result instanceof LogicalValidationResult.DependencyTerminal dep) {
            return tx.commitFailed(a.id(), dep.category(), dep.summary(), "validate");
        }
        return tx.commitFailed(a.id(), OrchestrationFailureCategory.INVARIANT_VIOLATION,
                "logical validation invariant", "validate");
    }

    private OrchestrationOutcome allocateStep(Snapshot a, String correlationId) {
        AllocationView view = a.view();
        List<ExecutionResolution> executionResults;
        try {
            executionResults = tradingClient.resolveExecutions(view.execIds());
        } catch (DependencyCallException e) {
            return handleDependencyFailure(a, e, "allocate");
        }

        SelectionResult selection = selector.select(new SelectionRequest(
                view.quantity(), view.instrumentSymbol(), view.side(),
                candidates(a.allocId(), executionResults)));

        if (selection instanceof SelectionResult.Complete complete) {
            List<String> internalIds = complete.selections().stream()
                    .map(SelectionResult.SelectedExecution::internalId).toList();
            try {
                tradingClient.allocate(a.allocId(), internalIds);
            } catch (DependencyCallException e) {
                if (e.isRetryable()) {
                    return scheduleRetry(a, e.toCategory(), e.getMessage(), "allocate");
                }
                return tx.commitRejected(a.id(), "execution allocation conflict", "allocate");
            }
            return tx.commitAllocated(a.id(), complete.selections(), correlationId);
        }
        if (selection instanceof SelectionResult.Insufficient) {
            return tx.commitRejected(a.id(), "insufficient eligible execution quantity", "allocate");
        }
        if (selection instanceof SelectionResult.ExcessQuantity) {
            return tx.commitRejected(a.id(),
                    "eligible executions exceed the allocation quantity (executions are allocated whole)",
                    "allocate");
        }
        if (selection instanceof SelectionResult.NoEligibleExecutions) {
            return tx.commitRejected(a.id(), "no eligible executions to allocate", "allocate");
        }
        return tx.commitFailed(a.id(), OrchestrationFailureCategory.TERMINAL_ORCHESTRATION,
                "execution selection could not proceed", "allocate");
    }

    private OrchestrationOutcome releaseStep(Snapshot a, String correlationId) {
        // Re-checked authoritatively in commitReleased; this avoids releasing for an original that a
        // concurrent Replace/Cancel already retired since this instruction validated.
        ExistingAllocationView original = reader.existingOriginal(a.refAllocId(), a.id()).orElse(null);
        if (original == null || !original.status().isSupersedable()) {
            return tx.commitRejected(a.id(), "original allocation " + a.refAllocId() + " is no longer live"
                    + (original == null ? "" : " (" + original.status() + ")"), "release");
        }
        try {
            tradingClient.release(a.refAllocId(), null);
        } catch (DependencyCallException e) {
            return handleDependencyFailure(a, e, "release");
        }
        return tx.commitReleased(a.id(), correlationId);
    }

    private OrchestrationOutcome completeStep(Snapshot a, String correlationId) {
        boolean emitSettlement = a.transType() != AllocTransType.CANCEL;
        OrchestrationOutcome outcome = tx.commitCompleted(a.id(), correlationId, emitSettlement);
        if (outcome == OrchestrationOutcome.COMPLETED) {
            dedupFilter.markProcessed(businessKey(a));
        }
        return outcome;
    }

    // --- failure handling ----------------------------------------------------------------------

    private OrchestrationOutcome handleDependencyFailure(Snapshot a, DependencyCallException e, String step) {
        if (e.isRetryable()) {
            return scheduleRetry(a, e.toCategory(), e.getMessage(), step);
        }
        return tx.commitFailed(a.id(), e.toCategory(), e.getMessage(), step);
    }

    private OrchestrationOutcome scheduleRetry(Snapshot a, OrchestrationFailureCategory category,
                                               String summary, String step) {
        Instant nextAttempt = clock.instant().plus(backoff(a.attemptCount()));
        return tx.commitRetryOrFail(a.id(), category, summary, nextAttempt, a.attemptsExhausted(), step);
    }

    private Duration backoff(int attemptCount) {
        double factor = Math.pow(properties.getRetryBackoffMultiplier(), Math.max(0, attemptCount - 1));
        long millis = (long) (properties.getRetryBackoffBase().toMillis() * factor);
        return Duration.ofMillis(Math.min(millis, properties.getRetryBackoffMax().toMillis()));
    }

    // --- projections (all from the plain snapshot view) ----------------------------------------

    private List<AccountValidationClient.Check> accountChecks(AllocationView view) {
        return view.accounts().stream()
                .map(line -> new AccountValidationClient.Check(line.accountId(), view.currency(),
                        line.netMoney(), view.instrumentSymbol(), line.allocQty()))
                .toList();
    }

    private InstrumentResolutionClient.Key instrumentKey(AllocationView view) {
        return new InstrumentResolutionClient.Key("EXCHANGE_SYMBOL", view.instrumentSymbol(),
                view.securityExchange());
    }

    /** Build selection candidates, treating executions already allocated to THIS allocId as ours. */
    private List<SelectionCandidate> candidates(String allocId, List<ExecutionResolution> resolutions) {
        return resolutions.stream().map(r -> {
            boolean own = "ALREADY_ALLOCATED".equals(r.reason()) && allocId.equals(r.allocationId());
            boolean eligible = r.eligible() || own;
            BigDecimal allocatable = own ? r.executedQuantity() : r.allocatableQuantity();
            return new SelectionCandidate(r.internalId(), r.execId(), r.instrumentId(), r.side(),
                    allocatable, r.transactTime(), eligible);
        }).toList();
    }

    private static String summarize(List<LogicalValidationError> errors) {
        return errors.stream().limit(5)
                .map(e -> e.code().name() + (e.identifier().isBlank() ? "" : "(" + e.identifier() + ")"))
                .reduce((x, y) -> x + "; " + y).orElse("business validation failed");
    }

    private static String businessKey(Snapshot a) {
        return a.allocId() + ":" + a.transType().code();
    }

    private String resolveCorrelationId(Long rawMessageId) {
        return rawMessages.findById(rawMessageId)
                .map(raw -> raw.getCorrelationId())
                .filter(id -> id != null && !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
