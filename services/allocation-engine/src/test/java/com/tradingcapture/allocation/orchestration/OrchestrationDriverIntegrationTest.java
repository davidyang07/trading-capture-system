package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recovery/orchestration driver's claim semantics (Increment 10): eligibility, deterministic
 * bounded batches, retry-time gating, terminal/exhausted skipping, and lease-based exclusion +
 * reclamation. Uses the claim transaction directly (no remote calls) with a controllable clock via
 * explicit {@code now}/lease arguments — no sleeps.
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
    void skipsTerminalAndAttemptExhaustedRows() {
        Long rejected = receivedNew("A-C2-REJ");
        jdbc.update("UPDATE allocations SET status = 'REJECTED' WHERE id = ?", rejected);
        Long exhausted = receivedNew("A-C2-EXH");
        jdbc.update("UPDATE allocations SET attempt_count = max_attempts WHERE id = ?", exhausted);

        assertThat(claimService.claim("worker-a", T, LEASE_CUTOFF, 10)).isEmpty();
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
