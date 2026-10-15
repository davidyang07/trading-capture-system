package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recovery/orchestration driver's claim semantics (Increment 10): eligibility, deterministic
 * bounded batches, retry-time gating, terminal skipping, failing stranded exhausted rows, and
 * lease-based exclusion + reclamation. Uses the claim transaction directly (no remote calls) with a
 * controllable clock via explicit {@code now}/lease arguments — no sleeps.
 */
class OrchestrationDriverIntegrationTest extends OrchestrationIntegrationTestSupport {

    @Autowired
    private OrchestrationClaimService claimService;

    private static final Instant T = Instant.parse("2026-07-17T14:00:00Z");
    private static final Instant LEASE_CUTOFF = T.minus(Duration.ofMinutes(2));

    private Long receivedNew(String allocId) {
        return persistAllocation(allocId, AllocTransType.NEW, null, "AAPL", "1", "100.0000",
                List.of(new AccountQty("ACC-1", "100.0000")), List.of("E1"));
    }

    @Test
    void claimsEligibleReceivedWorkAndStampsLeaseAndAttempt() {
        Long id = receivedNew("A-C1");

        List<Long> claimed = claimService.claim("worker-a", T, LEASE_CUTOFF, 10);

        assertThat(claimed).containsExactly(id);
        assertThat(reload(id).getClaimedBy()).isEqualTo("worker-a");
        assertThat(reload(id).getAttemptCount()).isEqualTo(1);
    }

    @Test
    void skipsTerminalRowsAndFailsExhaustedOnesInsteadOfStrandingThem() {
        Long rejected = receivedNew("A-C2-REJ");
        jdbc.update("UPDATE allocations SET status = 'REJECTED' WHERE id = ?", rejected);
        Long exhausted = receivedNew("A-C2-EXH");
        jdbc.update("UPDATE allocations SET attempt_count = max_attempts WHERE id = ?", exhausted);

        assertThat(claimService.claim("worker-a", T, LEASE_CUTOFF, 10)).isEmpty();

        assertThat(reload(rejected).getStatus()).isEqualTo(AllocationStatus.REJECTED);
        Allocation failed = reload(exhausted);
        assertThat(failed.getStatus()).isEqualTo(AllocationStatus.FAILED);
        assertThat(failed.getFailureCategory()).isEqualTo(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION);
        assertThat(failed.getFailureSummary()).startsWith("attempts exhausted in RECEIVED");
        // The failure is announced; nothing can be bound before validation, so nothing to release.
        assertThat(outboxCount("ExceptionReported", "A-C2-EXH")).isEqualTo(1);
        assertThat(failed.isExecutionsReleasePending()).isFalse();
    }

    @Test
    void lastAttemptAbandonedMidStepIsFailedOnceItsLeaseExpires() {
        // Executions are bound, then the final attempt dies without recording any outcome (crash,
        // version conflict, unexpected error): the row keeps its claim and has no attempts left.
        Long id = receivedNew("A-C6");
        jdbc.update("""
                UPDATE allocations SET status = 'EXECUTIONS_ALLOCATED', attempt_count = max_attempts,
                    claimed_by = 'worker-dead', claimed_at = ? WHERE id = ?
                """, java.sql.Timestamp.from(T), id);

        // While the lease is fresh it belongs to its worker.
        Instant oneMinuteLater = T.plus(Duration.ofMinutes(1));
        claimService.claim("worker-b", oneMinuteLater, oneMinuteLater.minus(Duration.ofMinutes(2)), 10);
        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.EXECUTIONS_ALLOCATED);

        // Once it expires the row is failed terminally rather than left invisible forever.
        Instant threeMinutesLater = T.plus(Duration.ofMinutes(3));
        assertThat(claimService.claim("worker-b", threeMinutesLater, threeMinutesLater.minus(Duration.ofMinutes(2)), 10))
                .isEmpty();
        Allocation failed = reload(id);
        assertThat(failed.getStatus()).isEqualTo(AllocationStatus.FAILED);
        assertThat(failed.getClaimedBy()).isNull();
        assertThat(jdbc.queryForMap("""
                SELECT from_status, to_status FROM allocation_state_transitions
                WHERE allocation_id = ? ORDER BY seq DESC LIMIT 1
                """, id))
                .containsEntry("from_status", "EXECUTIONS_ALLOCATED")
                .containsEntry("to_status", "FAILED");
        // Its executions are bound in trading-service, so they are queued for release.
        assertThat(failed.isExecutionsReleasePending()).isTrue();
        assertThat(outboxCount("ExceptionReported", "A-C6")).isEqualTo(1);
    }

    @Test
    void enforcesBatchSizeInDeterministicIdOrder() {
        Long a = receivedNew("A-C3-a");
        Long b = receivedNew("A-C3-b");
        receivedNew("A-C3-c");

        assertThat(claimService.claim("worker-a", T, LEASE_CUTOFF, 2)).containsExactly(a, b);
    }

    @Test
    void doesNotClaimWorkWhoseRetryIsNotYetDue() {
        Long id = receivedNew("A-C4");
        jdbc.update("UPDATE allocations SET next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(T.plus(Duration.ofMinutes(5))), id);

        assertThat(claimService.claim("worker-a", T, LEASE_CUTOFF, 10)).isEmpty();
    }

    @Test
    void aFreshLeaseIsNotStolenButAnExpiredOneIsReclaimed() {
        Long id = receivedNew("A-C5");
        claimService.claim("worker-a", T, LEASE_CUTOFF, 10); // sets claimed_at = T

        // A second worker one minute later still sees a fresh lease -> nothing to claim.
        Instant oneMinuteLater = T.plus(Duration.ofMinutes(1));
        assertThat(claimService.claim("worker-b", oneMinuteLater, oneMinuteLater.minus(Duration.ofMinutes(2)), 10))
                .isEmpty();

        // Three minutes after the original claim, the lease has expired -> reclaimable.
        Instant threeMinutesLater = T.plus(Duration.ofMinutes(3));
        assertThat(claimService.claim("worker-b", threeMinutesLater, threeMinutesLater.minus(Duration.ofMinutes(2)), 10))
                .containsExactly(id);
        assertThat(reload(id).getClaimedBy()).isEqualTo("worker-b");
        assertThat(reload(id).getAttemptCount()).isEqualTo(2);
    }
}
