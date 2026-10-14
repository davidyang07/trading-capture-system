package com.tradingcapture.allocation.selection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The typed outcome of deterministic execution selection. Partial allocation is not permitted for an
 * allocation and executions are consumed whole, so eligible quantity below or above the request is a
 * typed {@link Insufficient} / {@link ExcessQuantity} failure, never a partial success. Results are
 * independent of JPA/HTTP types.
 */
public sealed interface SelectionResult
        permits SelectionResult.Complete, SelectionResult.Insufficient,
        SelectionResult.ExcessQuantity, SelectionResult.NoEligibleExecutions,
        SelectionResult.InvalidCandidate, SelectionResult.Invariant {

    boolean isComplete();

    /** A full selection of whole executions whose quantities sum exactly to the requested quantity. */
    record Complete(List<SelectedExecution> selections, BigDecimal totalSelected) implements SelectionResult {
        public Complete {
            selections = List.copyOf(selections);
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    }

    /** Eligible executions exist but their aggregate quantity is below the request (no partial fill). */
    record Insufficient(BigDecimal availableQuantity, BigDecimal requestedQuantity) implements SelectionResult {
        @Override
        public boolean isComplete() {
            return false;
        }
    }

    /**
     * Eligible executions exceed the request. Executions are consumed whole, so this could only be
     * met by splitting one — which would bind (and strand) its remainder.
     */
    record ExcessQuantity(BigDecimal availableQuantity, BigDecimal requestedQuantity) implements SelectionResult {
        @Override
        public boolean isComplete() {
            return false;
        }
    }

    /** No candidate is eligible (none match instrument/side/lifecycle with positive quantity). */
    record NoEligibleExecutions() implements SelectionResult {
        @Override
        public boolean isComplete() {
            return false;
        }
    }

    /** A candidate (or the request) carried malformed data that prevents deterministic selection. */
    record InvalidCandidate(String reason) implements SelectionResult {
        @Override
        public boolean isComplete() {
            return false;
        }
    }

    /** A programming/consistency defect that should never occur. */
    record Invariant(String reason) implements SelectionResult {
        @Override
        public boolean isComplete() {
            return false;
        }
    }

    /** One execution chosen for the allocation, with its before/after availability and 1-based sequence. */
    record SelectedExecution(String internalId,
                             String execId,
                             BigDecimal selectedQuantity,
                             BigDecimal availableBefore,
                             BigDecimal availableAfter,
                             Instant transactTime,
                             int sequence) {
    }
}
