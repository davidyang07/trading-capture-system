package com.tradingcapture.allocation.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The ADR-0006 persisted allocation state machine, as an explicit legal-transition table that
 * Phase 8 orchestration consults before every mutation. Illegal transitions throw
 * {@link IllegalAllocationTransitionException}; terminal states have no outgoing edges, except the
 * supersession edges out of {@code COMPLETED} described below.
 *
 * <p>Canonical path (ADR-0006):
 * {@code RECEIVED -> (INVALID | VALIDATED | REJECTED) -> (EXECUTIONS_RELEASED ->)? EXECUTIONS_ALLOCATED -> COMPLETED},
 * with {@code FAILED} (retryable failure exhausted) reachable from any non-terminal working
 * state. {@code INVALID} is only ever an ingestion outcome (Phase 7) — orchestration never
 * transitions <em>into</em> it — so it appears only as a terminal with no outgoing edges.
 * Supersession: when a Replace/Cancel releases the allocation it references, that original moves
 * {@code (EXECUTIONS_ALLOCATED | COMPLETED) -> (REPLACED | CANCELLED)} in the same transaction.</p>
 *
 * <ul>
 *   <li>{@code RECEIVED}: awaiting logical validation (Phase 7 left it here).</li>
 *   <li>{@code VALIDATED}: passed logical (cross-service) validation.</li>
 *   <li>{@code EXECUTIONS_RELEASED}: a Replace/Cancel released the referenced allocation's
 *       executions; for Replace it re-allocates ({@code -> EXECUTIONS_ALLOCATED}), for Cancel it
 *       completes ({@code -> COMPLETED}).</li>
 *   <li>{@code EXECUTIONS_ALLOCATED}: executions bound in trading-service.</li>
 *   <li>{@code COMPLETED}: settlement requested (New/Replace) or cancel finalized — terminal.</li>
 *   <li>{@code REJECTED}: business validation failure — terminal.</li>
 *   <li>{@code FAILED}: retryable dependency/infra failure whose attempts were exhausted — terminal.</li>
 *   <li>{@code REPLACED} / {@code CANCELLED}: superseded by a later Replace / Cancel instruction
 *       whose release step unbound this allocation's executions — terminal dead-ends.</li>
 * </ul>
 *
 * <p>{@code COMPLETED} is terminal for its own orchestration ({@link AllocationStatus#isTerminal()})
 * but keeps the two supersession edges, so a Replace/Cancel can still retire it; every other
 * terminal state is a dead-end.</p>
 */
public final class AllocationStateMachine {

    private static final Map<AllocationStatus, Set<AllocationStatus>> LEGAL =
            new EnumMap<>(AllocationStatus.class);

    static {
        LEGAL.put(AllocationStatus.RECEIVED, Set.of(
                AllocationStatus.VALIDATED, AllocationStatus.REJECTED, AllocationStatus.FAILED));
        // REJECTED is reachable from VALIDATED and EXECUTIONS_RELEASED too: a business rule can only
        // be decided at the allocate step (executions are re-resolved there, so availability may have
        // dropped, or trading-service may return an allocation conflict). Such an allocate-time
        // business rejection is a legal terminal, not a stuck state.
        LEGAL.put(AllocationStatus.VALIDATED, Set.of(
                AllocationStatus.EXECUTIONS_ALLOCATED, AllocationStatus.EXECUTIONS_RELEASED,
                AllocationStatus.REJECTED, AllocationStatus.FAILED));
        LEGAL.put(AllocationStatus.EXECUTIONS_RELEASED, Set.of(
                AllocationStatus.EXECUTIONS_ALLOCATED, AllocationStatus.COMPLETED,
                AllocationStatus.REJECTED, AllocationStatus.FAILED));
        LEGAL.put(AllocationStatus.EXECUTIONS_ALLOCATED, Set.of(
                AllocationStatus.COMPLETED, AllocationStatus.EXECUTIONS_RELEASED,
                AllocationStatus.FAILED, AllocationStatus.REPLACED, AllocationStatus.CANCELLED));
        // Terminal for orchestration; a later Replace/Cancel may still supersede it.
        LEGAL.put(AllocationStatus.COMPLETED, Set.of(
                AllocationStatus.REPLACED, AllocationStatus.CANCELLED));
        // Terminal dead-ends — no outgoing transitions.
        LEGAL.put(AllocationStatus.INVALID, Set.of());
        LEGAL.put(AllocationStatus.REJECTED, Set.of());
        LEGAL.put(AllocationStatus.FAILED, Set.of());
        LEGAL.put(AllocationStatus.REPLACED, Set.of());
        LEGAL.put(AllocationStatus.CANCELLED, Set.of());
    }

    private AllocationStateMachine() {
    }

    /** The states legally reachable in one step from {@code from} (empty for terminals). */
    public static Set<AllocationStatus> legalNextStates(AllocationStatus from) {
        Set<AllocationStatus> next = LEGAL.get(from);
        if (next == null) {
            throw new IllegalArgumentException("No transition table entry for " + from);
        }
        return next;
    }

    public static boolean isLegal(AllocationStatus from, AllocationStatus to) {
        return legalNextStates(from).contains(to);
    }

    /** @throws IllegalAllocationTransitionException if {@code from -> to} is not permitted. */
    public static void assertLegal(AllocationStatus from, AllocationStatus to) {
        if (!isLegal(from, to)) {
            throw new IllegalAllocationTransitionException(from, to);
        }
    }
}
