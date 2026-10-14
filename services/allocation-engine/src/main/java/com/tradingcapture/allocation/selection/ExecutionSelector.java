package com.tradingcapture.allocation.selection;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic execution selection (ADR-0017). Selects, from a snapshot of resolved executions,
 * every eligible execution the instruction lists, each consumed <em>whole</em>, sequenced by a total
 * order that is independent of input order: FIFO by execution {@code transactTime}, tie-broken by
 * {@code execId} then {@code internalId}.
 *
 * <p>Executions are never split: trading-service binds a whole execution to exactly one allocation,
 * so consuming part of one would bind all of it and strand the remainder (no later allocation could
 * use it, and the engine's assignment quantity would disagree with trading's). The eligible
 * executions must therefore sum exactly to the requested quantity: less is a typed
 * {@link SelectionResult.Insufficient}, more is a typed {@link SelectionResult.ExcessQuantity} —
 * never a partial success.</p>
 *
 * <p>This is a pure calculation over a snapshot: it does <em>not</em> reserve quantity. The New/
 * Replace transaction re-checks authoritative database state under locking before persisting, so a
 * stale selection fails safely there and retries cannot over-allocate.</p>
 */
@Component
public class ExecutionSelector {

    private static final int QUANTITY_SCALE = 4;

    /** FIFO total order: earliest execution first, then stable identifiers. Input-order independent. */
    private static final Comparator<SelectionCandidate> FIFO_ORDER =
            Comparator.comparing(SelectionCandidate::transactTime)
                    .thenComparing(SelectionCandidate::execId)
                    .thenComparing(SelectionCandidate::internalId);

    public SelectionResult select(SelectionRequest request) {
        if (request == null || request.candidates() == null) {
            return new SelectionResult.Invariant("selection request is incomplete");
        }
        BigDecimal requested = request.requestedQuantity();
        if (requested == null || requested.signum() <= 0) {
            return new SelectionResult.InvalidCandidate("requested quantity must be positive");
        }
        if (requested.scale() > QUANTITY_SCALE) {
            return new SelectionResult.InvalidCandidate("requested quantity exceeds supported precision");
        }

        List<SelectionCandidate> eligible = new ArrayList<>();
        for (SelectionCandidate candidate : request.candidates()) {
            if (!isEligible(candidate, request)) {
                continue;
            }
            SelectionResult.InvalidCandidate invalid = validate(candidate);
            if (invalid != null) {
                return invalid;
            }
            eligible.add(candidate);
        }
        if (eligible.isEmpty()) {
            return new SelectionResult.NoEligibleExecutions();
        }
        eligible.sort(FIFO_ORDER);

        BigDecimal available = eligible.stream()
                .map(SelectionCandidate::allocatableQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(requested) < 0) {
            return new SelectionResult.Insufficient(scaled(available), scaled(requested));
        }
        if (available.compareTo(requested) > 0) {
            return new SelectionResult.ExcessQuantity(scaled(available), scaled(requested));
        }

        List<SelectionResult.SelectedExecution> selections = new ArrayList<>();
        int sequence = 1;
        for (SelectionCandidate candidate : eligible) {
            BigDecimal whole = scaled(candidate.allocatableQuantity());
            selections.add(new SelectionResult.SelectedExecution(
                    candidate.internalId(), candidate.execId(), whole,
                    whole, scaled(BigDecimal.ZERO), candidate.transactTime(), sequence++));
        }
        return new SelectionResult.Complete(selections, scaled(requested));
    }

    private static boolean isEligible(SelectionCandidate candidate, SelectionRequest request) {
        return candidate != null
                && candidate.eligible()
                && matches(request.instrumentId(), candidate.instrumentId())
                && matches(request.side(), candidate.side())
                && candidate.allocatableQuantity() != null
                && candidate.allocatableQuantity().signum() > 0;
    }

    private static SelectionResult.InvalidCandidate validate(SelectionCandidate candidate) {
        if (candidate.internalId() == null || candidate.internalId().isBlank()
                || candidate.execId() == null || candidate.execId().isBlank()) {
            return new SelectionResult.InvalidCandidate("execution candidate is missing an identifier");
        }
        if (candidate.transactTime() == null) {
            return new SelectionResult.InvalidCandidate(
                    "execution " + candidate.execId() + " has no ordering timestamp");
        }
        if (candidate.allocatableQuantity().scale() > QUANTITY_SCALE) {
            return new SelectionResult.InvalidCandidate(
                    "execution " + candidate.execId() + " quantity exceeds supported precision");
        }
        return null;
    }

    private static boolean matches(String expected, String actual) {
        // A null request-side/instrument is not a filter; a present value must match exactly.
        return expected == null || expected.equals(actual);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, java.math.RoundingMode.UNNECESSARY);
    }
}
