package com.tradingcapture.allocation.domain.model;

/**
 * Thrown when Phase 8 orchestration attempts a state transition the ADR-0006 machine does not
 * permit (including any move out of a terminal state). Programming/consistency error — never a
 * normal control-flow path — so it is unchecked.
 */
public class IllegalAllocationTransitionException extends IllegalStateException {

    private final AllocationStatus from;
    private final AllocationStatus to;

    public IllegalAllocationTransitionException(AllocationStatus from, AllocationStatus to) {
        super("Illegal allocation transition " + from + " -> " + to
                + " (ADR-0006); legal next states from " + from + " are "
                + AllocationStateMachine.legalNextStates(from));
        this.from = from;
        this.to = to;
    }

    public AllocationStatus getFrom() {
        return from;
    }

    public AllocationStatus getTo() {
        return to;
    }
}
