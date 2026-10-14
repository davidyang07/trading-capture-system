package com.tradingcapture.allocation.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The ADR-0006 legal-transition table and the {@link Allocation#transitionTo} guard: every legal
 * edge is accepted, every other edge (and every move out of a dead-end) is rejected, and the enum's
 * terminal set matches the table's dead-ends plus {@code COMPLETED}, which only keeps the
 * supersession edges ({@code -> REPLACED / CANCELLED}).
 */
class AllocationStateMachineTest {

    private static final Set<AllocationStatus> TERMINALS = EnumSet.of(
            AllocationStatus.INVALID, AllocationStatus.REJECTED,
            AllocationStatus.COMPLETED, AllocationStatus.FAILED,
            AllocationStatus.REPLACED, AllocationStatus.CANCELLED);

    private static final Set<AllocationStatus> SUPERSESSION =
            EnumSet.of(AllocationStatus.REPLACED, AllocationStatus.CANCELLED);

    // The canonical table, restated independently of the production map so a silent edit to
    // either side is caught.
    private static Set<AllocationStatus> expectedLegal(AllocationStatus from) {
        return switch (from) {
            case RECEIVED -> EnumSet.of(AllocationStatus.VALIDATED, AllocationStatus.REJECTED,
                    AllocationStatus.FAILED);
            case VALIDATED -> EnumSet.of(AllocationStatus.EXECUTIONS_ALLOCATED,
                    AllocationStatus.EXECUTIONS_RELEASED, AllocationStatus.REJECTED,
                    AllocationStatus.FAILED);
            case EXECUTIONS_RELEASED -> EnumSet.of(AllocationStatus.EXECUTIONS_ALLOCATED,
                    AllocationStatus.COMPLETED, AllocationStatus.REJECTED, AllocationStatus.FAILED);
            case EXECUTIONS_ALLOCATED -> EnumSet.of(AllocationStatus.COMPLETED,
                    AllocationStatus.EXECUTIONS_RELEASED, AllocationStatus.FAILED,
                    AllocationStatus.REPLACED, AllocationStatus.CANCELLED);
            case COMPLETED -> EnumSet.of(AllocationStatus.REPLACED, AllocationStatus.CANCELLED);
            case INVALID, REJECTED, FAILED, REPLACED, CANCELLED -> EnumSet.noneOf(AllocationStatus.class);
        };
    }

    @ParameterizedTest
    @EnumSource(AllocationStatus.class)
    void legalNextStatesMatchTheCanonicalTable(AllocationStatus from) {
        assertThat(AllocationStateMachine.legalNextStates(from))
                .containsExactlyInAnyOrderElementsOf(expectedLegal(from));
    }

    @ParameterizedTest
    @EnumSource(AllocationStatus.class)
    void everyLegalEdgeIsAcceptedAndEveryIllegalEdgeIsRejected(AllocationStatus from) {
        for (AllocationStatus to : AllocationStatus.values()) {
            if (expectedLegal(from).contains(to)) {
                assertThat(AllocationStateMachine.isLegal(from, to)).isTrue();
                assertThatNoException().isThrownBy(() -> AllocationStateMachine.assertLegal(from, to));
            } else {
                assertThat(AllocationStateMachine.isLegal(from, to)).isFalse();
                assertThatExceptionOfType(IllegalAllocationTransitionException.class)
                        .isThrownBy(() -> AllocationStateMachine.assertLegal(from, to))
                        .satisfies(ex -> {
                            assertThat(ex.getFrom()).isEqualTo(from);
                            assertThat(ex.getTo()).isEqualTo(to);
                        });
            }
        }
    }

    @ParameterizedTest
    @EnumSource(AllocationStatus.class)
    void terminalStatesHaveNoOrchestrationEdgesAndAgreeWithTheEnum(AllocationStatus state) {
        // Terminal = orchestration never advances it: no outgoing edge other than supersession.
        Set<AllocationStatus> orchestrationEdges = EnumSet.noneOf(AllocationStatus.class);
        orchestrationEdges.addAll(AllocationStateMachine.legalNextStates(state));
        if (state == AllocationStatus.COMPLETED) {
            orchestrationEdges.removeAll(SUPERSESSION);
        }
        assertThat(orchestrationEdges.isEmpty())
                .as("terminal-for-orchestration must match AllocationStatus.isTerminal() for %s", state)
                .isEqualTo(state.isTerminal());
        assertThat(TERMINALS.contains(state)).isEqualTo(state.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(AllocationStatus.class)
    void onlyLiveAllocationsCanBeSuperseded(AllocationStatus state) {
        boolean supersedable = AllocationStateMachine.legalNextStates(state).containsAll(SUPERSESSION);
        assertThat(supersedable).isEqualTo(state.isSupersedable());
    }

    @Test
    void supersessionRetiresTheOriginalAndRecordsTheSuccessor() {
        Allocation original = receivedAllocation();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        original.transitionTo(AllocationStatus.VALIDATED, now);
        original.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, now);
        original.transitionTo(AllocationStatus.COMPLETED, now);

        Allocation replacement = new Allocation("ALLOC-R", AllocTransType.REPLACE, AllocationStatus.RECEIVED, 2L, now);
        original.markSupersededBy(replacement, now);

        assertThat(original.getStatus()).isEqualTo(AllocationStatus.REPLACED);
        assertThat(original.getTransitions()).last()
                .satisfies(t -> assertThat(t.getReason()).isEqualTo("replaced by ALLOC-R"));
        // A superseded allocation is a dead-end: it cannot be cancelled afterwards.
        Allocation cancel = new Allocation("ALLOC-C", AllocTransType.CANCEL, AllocationStatus.RECEIVED, 3L, now);
        assertThatExceptionOfType(IllegalAllocationTransitionException.class)
                .isThrownBy(() -> original.markSupersededBy(cancel, now));
    }

    @Test
    void newAllocationHappyPathTransitionsThroughTheCanonicalStates() {
        Allocation allocation = receivedAllocation();
        Instant t1 = Instant.parse("2026-07-17T00:00:01Z");
        allocation.transitionTo(AllocationStatus.VALIDATED, t1);
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.VALIDATED);
        assertThat(allocation.getUpdatedAt()).isEqualTo(t1);

        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, Instant.parse("2026-07-17T00:00:02Z"));
        allocation.transitionTo(AllocationStatus.COMPLETED, Instant.parse("2026-07-17T00:00:03Z"));
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.COMPLETED);
    }

    @Test
    void replaceReleaseThenReallocateIsLegal() {
        Allocation allocation = receivedAllocation();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        allocation.transitionTo(AllocationStatus.VALIDATED, now);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_RELEASED, now);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, now);
        allocation.transitionTo(AllocationStatus.COMPLETED, now);
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.COMPLETED);
    }

    @Test
    void transitionOutOfATerminalStateThrows() {
        Allocation allocation = receivedAllocation();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        allocation.transitionTo(AllocationStatus.REJECTED, now);
        assertThatExceptionOfType(IllegalAllocationTransitionException.class)
                .isThrownBy(() -> allocation.transitionTo(AllocationStatus.VALIDATED, now));
    }

    private static Allocation receivedAllocation() {
        return new Allocation("ALLOC-1", AllocTransType.NEW, AllocationStatus.RECEIVED, 1L,
                Instant.parse("2026-07-17T00:00:00Z"));
    }
}
