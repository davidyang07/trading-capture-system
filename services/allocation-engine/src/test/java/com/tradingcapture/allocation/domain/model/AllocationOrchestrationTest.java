package com.tradingcapture.allocation.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The Phase 8 orchestration bookkeeping on {@link Allocation} and {@link AllocationAssignment}:
 * attempt/retry/failure accounting, terminal transitions, execution assignment and
 * exactly-once release, and the append-only transition history — all at the in-memory domain
 * tier, independent of persistence.
 */
class AllocationOrchestrationTest {

    private static final Instant T0 = Instant.parse("2026-07-17T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-17T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-07-17T00:00:02Z");

    private static Allocation received() {
        return new Allocation("ALLOC-1", AllocTransType.NEW, AllocationStatus.RECEIVED, 1L, T0);
    }

    // --- attempts ------------------------------------------------------------------------------

    @Test
    void recordAttemptIncrementsCountAndStampsLastAttempt() {
        Allocation allocation = received();
        assertThat(allocation.getAttemptCount()).isZero();

        allocation.recordAttempt(T1);

        assertThat(allocation.getAttemptCount()).isEqualTo(1);
        assertThat(allocation.getLastAttemptAt()).isEqualTo(T1);
    }

    @Test
    void attemptsExhaustedWhenCountReachesMax() {
        Allocation allocation = received();
        assertThat(allocation.getMaxAttempts()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(allocation.attemptsExhausted()).isFalse();
            allocation.recordAttempt(T1);
        }
        assertThat(allocation.attemptsExhausted()).isTrue();
    }

    // --- retryable / transient failure bookkeeping ---------------------------------------------

    @Test
    void recordRetryableFailureSchedulesTheNextAttemptWithoutChangingState() {
        Allocation allocation = received();

        allocation.recordRetryableFailure(OrchestrationFailureCategory.DEPENDENCY_UNAVAILABLE,
                "account-service unreachable", T2, T1);

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.RECEIVED);
        assertThat(allocation.getFailureCategory())
                .isEqualTo(OrchestrationFailureCategory.DEPENDENCY_UNAVAILABLE);
        assertThat(allocation.getFailureSummary()).isEqualTo("account-service unreachable");
        assertThat(allocation.getNextAttemptAt()).isEqualTo(T2);
    }

    @Test
    void recordRetryableFailureRejectsNonRetryableCategory() {
        Allocation allocation = received();
        assertThatIllegalArgumentException().isThrownBy(() ->
                allocation.recordRetryableFailure(OrchestrationFailureCategory.BUSINESS_REJECTION,
                        "x", T2, T1));
    }

    @Test
    void clearTransientFailureResetsTheBookkeeping() {
        Allocation allocation = received();
        allocation.recordRetryableFailure(OrchestrationFailureCategory.TRANSIENT_INFRASTRUCTURE,
                "timeout", T2, T1);

        allocation.clearTransientFailure(T2);

        assertThat(allocation.getFailureCategory()).isNull();
        assertThat(allocation.getFailureSummary()).isNull();
        assertThat(allocation.getNextAttemptAt()).isNull();
    }

    // --- terminal transitions ------------------------------------------------------------------

    @Test
    void rejectMovesToRejectedWithBusinessCategoryAndCompletion() {
        Allocation allocation = received();

        allocation.reject("account ACC-9 is inactive", T1);

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.REJECTED);
        assertThat(allocation.getFailureCategory())
                .isEqualTo(OrchestrationFailureCategory.BUSINESS_REJECTION);
        assertThat(allocation.getFailureSummary()).isEqualTo("account ACC-9 is inactive");
        assertThat(allocation.getCompletedAt()).isEqualTo(T1);
        assertThat(allocation.getNextAttemptAt()).isNull();
    }

    @Test
    void failTerminallyMovesToFailedAndRejectsBusinessCategory() {
        Allocation allocation = received();

        allocation.failTerminally(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION,
                "retries exhausted", T1);

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.FAILED);
        assertThat(allocation.getFailureCategory())
                .isEqualTo(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION);
        assertThat(allocation.getCompletedAt()).isEqualTo(T1);

        Allocation other = received();
        assertThatIllegalArgumentException().isThrownBy(() ->
                other.failTerminally(OrchestrationFailureCategory.BUSINESS_REJECTION, "x", T1));
    }

    @Test
    void markCompletedClearsFailureBookkeepingAndStampsCompletion() {
        Allocation allocation = received();
        allocation.transitionTo(AllocationStatus.VALIDATED, T1);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, T1);

        allocation.markCompleted(T2);

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        assertThat(allocation.getCompletedAt()).isEqualTo(T2);
        assertThat(allocation.getFailureCategory()).isNull();
    }

    // --- transition history --------------------------------------------------------------------

    @Test
    void everyAcceptedTransitionAppendsSequentialHistoryWithAttemptContext() {
        Allocation allocation = received();
        allocation.recordAttempt(T1);
        allocation.transitionTo(AllocationStatus.VALIDATED, "logical validation passed", T1);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, T2);

        List<AllocationStateTransition> history = allocation.getTransitions();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSeq()).isEqualTo(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(AllocationStatus.RECEIVED);
        assertThat(history.get(0).getToStatus()).isEqualTo(AllocationStatus.VALIDATED);
        assertThat(history.get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(history.get(0).getReason()).isEqualTo("logical validation passed");
        assertThat(history.get(1).getSeq()).isEqualTo(2);
        assertThat(history.get(1).getToStatus()).isEqualTo(AllocationStatus.EXECUTIONS_ALLOCATED);
    }

    @Test
    void anIllegalTransitionLeavesStateAndHistoryUntouched() {
        Allocation allocation = received();
        allocation.reject("bad", T1);
        int historyBefore = allocation.getTransitions().size();

        assertThatExceptionOfType(IllegalAllocationTransitionException.class)
                .isThrownBy(() -> allocation.transitionTo(AllocationStatus.VALIDATED, T2));

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.REJECTED);
        assertThat(allocation.getTransitions()).hasSize(historyBefore);
    }

    // --- execution assignments -----------------------------------------------------------------

    @Test
    void assignExecutionRequiresPositiveQuantity() {
        Allocation allocation = received();
        assertThatIllegalArgumentException().isThrownBy(() ->
                allocation.assignExecution("EXEC-1", BigDecimal.ZERO, T1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                allocation.assignExecution("EXEC-1", new BigDecimal("-1"), T1));
    }

    @Test
    void assignExecutionRejectsDuplicateExecutionOnTheSameAllocation() {
        Allocation allocation = received();
        allocation.assignExecution("EXEC-1", new BigDecimal("100.0000"), T1);
        assertThatIllegalStateException().isThrownBy(() ->
                allocation.assignExecution("EXEC-1", new BigDecimal("50.0000"), T1));
    }

    @Test
    void netAllocatedQuantitySumsActiveAssignments() {
        Allocation allocation = received();
        allocation.assignExecution("EXEC-1", new BigDecimal("100.0000"), T1);
        allocation.assignExecution("EXEC-2", new BigDecimal("40.0000"), T1);

        assertThat(allocation.netAllocatedQuantity()).isEqualByComparingTo("140.0000");
    }

    @Test
    void releaseAllAssignmentsReleasesEachExactlyOnce() {
        Allocation allocation = received();
        AllocationAssignment a1 = allocation.assignExecution("EXEC-1", new BigDecimal("100.0000"), T1);
        AllocationAssignment a2 = allocation.assignExecution("EXEC-2", new BigDecimal("40.0000"), T1);

        List<String> released = allocation.releaseAllAssignments(T2);

        assertThat(released).containsExactly("EXEC-1", "EXEC-2");
        assertThat(a1.isReleased()).isTrue();
        assertThat(a1.getReleasedQty()).isEqualByComparingTo("100.0000");
        assertThat(a1.getReleasedAt()).isEqualTo(T2);
        assertThat(allocation.netAllocatedQuantity()).isEqualByComparingTo("0");

        // A second sweep releases nothing (already released) and the assignment refuses re-release.
        assertThat(allocation.releaseAllAssignments(T2)).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> a2.release(T2));
    }

    // --- releasing executions after an unsuccessful outcome ------------------------------------

    private static Allocation validated(AllocTransType type) {
        Allocation allocation = new Allocation("ALLOC-2", type, AllocationStatus.RECEIVED, 1L, T0);
        allocation.transitionTo(AllocationStatus.VALIDATED, T1);
        return allocation;
    }

    @Test
    void failingAfterValidationFlagsTheExecutionsForRelease() {
        Allocation allocation = validated(AllocTransType.NEW);

        allocation.failTerminally(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION, "retries exhausted", T2);

        assertThat(allocation.isExecutionsReleasePending()).isTrue();
        assertThat(allocation.getNextReleaseAt()).isEqualTo(T2);
    }

    @Test
    void rejectingAfterExecutionsWereAllocatedFlagsThemForRelease() {
        Allocation allocation = validated(AllocTransType.NEW);
        allocation.assignExecution("exec-1", new BigDecimal("100.0000"), T1);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, T1);

        allocation.failTerminally(OrchestrationFailureCategory.INVARIANT_VIOLATION, "broken", T2);

        assertThat(allocation.isExecutionsReleasePending()).isTrue();
    }

    @Test
    void endingBeforeValidationNeverFlagsARelease() {
        Allocation allocation = received();

        allocation.reject("inactive account", T1);

        assertThat(allocation.isExecutionsReleasePending()).isFalse();
        assertThat(allocation.getNextReleaseAt()).isNull();
    }

    @Test
    void aCancelNeverFlagsARelease() {
        Allocation allocation = validated(AllocTransType.CANCEL);

        allocation.failTerminally(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION, "retries exhausted", T2);

        assertThat(allocation.isExecutionsReleasePending()).isFalse();
    }

    @Test
    void aFailedReleaseIsCountedAndRescheduled() {
        Allocation allocation = validated(AllocTransType.NEW);
        allocation.reject("insufficient quantity", T1);
        Instant later = T2.plusSeconds(30);

        allocation.recordReleaseFailure("trading unavailable", later, T2);

        assertThat(allocation.isExecutionsReleasePending()).isTrue();
        assertThat(allocation.getReleaseAttemptCount()).isEqualTo(1);
        assertThat(allocation.getNextReleaseAt()).isEqualTo(later);
        assertThat(allocation.getLastReleaseError()).isEqualTo("trading unavailable");
    }

    @Test
    void completingTheReleaseClearsTheFlagAndReturnsTheAnnouncedExecutions() {
        Allocation allocation = validated(AllocTransType.NEW);
        allocation.assignExecution("exec-1", new BigDecimal("100.0000"), T1);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, T1);
        allocation.failTerminally(OrchestrationFailureCategory.INVARIANT_VIOLATION, "broken", T2);

        List<String> released = allocation.completeExecutionRelease(T2);

        assertThat(released).containsExactly("exec-1");
        assertThat(allocation.isExecutionsReleasePending()).isFalse();
        assertThat(allocation.getNextReleaseAt()).isNull();
        assertThat(allocation.netAllocatedQuantity()).isEqualByComparingTo("0");
    }
}
