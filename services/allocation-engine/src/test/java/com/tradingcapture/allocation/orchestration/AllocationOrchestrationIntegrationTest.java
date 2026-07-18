package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end orchestration over real MySQL/Redis with stubbed downstream services: New completion
 * with exact assignments and outbox events, business rejection, insufficient quantity, retry then
 * recovery, idempotent re-run (no duplicate events), Cancel release-exactly-once, and Replace
 * release-and-reallocate.
 */
class AllocationOrchestrationIntegrationTest extends OrchestrationIntegrationTestSupport {

    private Long persistNew(String allocId, String qty, List<String> execIds) {
        return persistAllocation(allocId, AllocTransType.NEW, null, "AAPL", "1", qty,
                List.of(new AccountQty("ACC-1", qty)), execIds);
    }

    private void stubAllOkFor(String execId, String qty) {
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible(execId, "exec_" + execId, qty, "2026-07-17T10:00:00Z");
        stubAllocationStateOk();
    }

    @Test
    void newAllocationCompletesWithExactAssignmentAndOutboxEvents() {
        Long id = persistNew("A-NEW-1", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");

        driver.runOnce();

        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        var assignments = assignmentsOf("A-NEW-1");
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0)).containsEntry("execution_id", "exec_E1")
                .containsEntry("status", "ASSIGNED");
        assertThat(new java.math.BigDecimal(assignments.get(0).get("allocated_qty").toString()))
                .isEqualByComparingTo("100.0000");
        assertThat(outboxCount("AllocationValidated", "A-NEW-1")).isEqualTo(1);
        assertThat(outboxCount("ExecutionsAllocated", "A-NEW-1")).isEqualTo(1);
        assertThat(outboxCount("SettlementRequested", "A-NEW-1")).isEqualTo(1);
    }

    @Test
    void inactiveAccountIsABusinessRejection() {
        Long id = persistNew("A-REJ-1", "100.0000", List.of("E1"));
        stubAccountsResponse("""
                { "results": [ { "accountId": "ACC-1", "found": true, "status": "SUSPENDED",
                  "active": false, "ownerSubject": "investor1", "sufficientCash": null,
                  "sufficientSecurities": null, "reasons": ["account not active"] } ] }
                """);
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E1", "exec_E1", "100.0000", "2026-07-17T10:00:00Z");

        driver.runOnce();

        Allocation a = reload(id);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.REJECTED);
        assertThat(a.getFailureCategory()).isEqualTo(OrchestrationFailureCategory.BUSINESS_REJECTION);
        assertThat(assignmentsOf("A-REJ-1")).isEmpty();
        assertThat(outboxCount("ExecutionsAllocated", "A-REJ-1")).isZero();
    }

    @Test
    void insufficientExecutionQuantityIsRejected() {
        Long id = persistNew("A-INS-1", "100.0000", List.of("E1"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E1", "exec_E1", "40.0000", "2026-07-17T10:00:00Z"); // only 40 available
        stubAllocationStateOk();

        driver.runOnce();

        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.REJECTED);
    }

    @Test
    void transientDependencyFailureSchedulesRetryThenRecovers() {
        Long id = persistNew("A-RETRY-1", "100.0000", List.of("E1"));
        stubAccounts5xx();

        driver.runOnce();

        Allocation afterFailure = reload(id);
        assertThat(afterFailure.getStatus()).isEqualTo(AllocationStatus.RECEIVED);
        assertThat(afterFailure.getAttemptCount()).isEqualTo(1);
        assertThat(afterFailure.getNextAttemptAt()).isNotNull();
        assertThat(afterFailure.getFailureCategory().isRetryable()).isTrue();

        // Make it due again and let the dependency recover.
        jdbc.update("UPDATE allocations SET next_attempt_at = TIMESTAMP('2020-01-01') WHERE id = ?", id);
        stubAllOkFor("E1", "100.0000");

        driver.runOnce();

        Allocation recovered = reload(id);
        assertThat(recovered.getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        assertThat(recovered.getAttemptCount()).isEqualTo(2);
        assertThat(recovered.getFailureCategory()).isNull(); // transient failure cleared on success
    }

    @Test
    void reRunOfACompletedAllocationProducesNoDuplicateEvents() {
        Long id = persistNew("A-IDEM-1", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");
        driver.runOnce();
        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        driver.runOnce(); // terminal — must not be re-claimed or re-emitted

        assertThat(outboxCount("AllocationValidated", "A-IDEM-1")).isEqualTo(1);
        assertThat(outboxCount("ExecutionsAllocated", "A-IDEM-1")).isEqualTo(1);
        assertThat(outboxCount("SettlementRequested", "A-IDEM-1")).isEqualTo(1);
    }

    @Test
    void cancelReleasesTheOriginalExactlyOnce() {
        Long originalId = persistNew("A-ORIG-1", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");
        driver.runOnce();
        assertThat(reload(originalId).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        Long cancelId = persistAllocation("A-CANCEL-1", AllocTransType.CANCEL, "A-ORIG-1", "AAPL", "1",
                "100.0000", List.of(new AccountQty("ACC-1", "100.0000")), List.of("E1"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E1", "exec_E1", "100.0000", "2026-07-17T10:00:00Z");
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/internal/v1/executions/allocation-state"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.okJson("""
                        { "allocationId": "A-ORIG-1", "action": "RELEASE", "appliedCount": 1, "executions": [] }
                        """)));

        driver.runOnce();

        assertThat(reload(cancelId).getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        assertThat(assignmentsOf("A-ORIG-1")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("status", "RELEASED"));
        assertThat(outboxCount("ExecutionsReleased", "A-CANCEL-1")).isEqualTo(1);
        assertThat(outboxCount("SettlementRequested", "A-CANCEL-1")).isZero(); // cancel does not settle

        driver.runOnce(); // idempotent
        assertThat(outboxCount("ExecutionsReleased", "A-CANCEL-1")).isEqualTo(1);
    }

    @Test
    void replaceReleasesOriginalAndReallocates() {
        Long originalId = persistNew("A-ORIG-2", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");
        driver.runOnce();
        assertThat(reload(originalId).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        Long replaceId = persistAllocation("A-REPL-1", AllocTransType.REPLACE, "A-ORIG-2", "AAPL", "1",
                "100.0000", List.of(new AccountQty("ACC-1", "100.0000")), List.of("E2"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E2", "exec_E2", "100.0000", "2026-07-17T11:00:00Z");
        stubAllocationStateOk();

        driver.runOnce();

        assertThat(reload(replaceId).getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        assertThat(assignmentsOf("A-ORIG-2").get(0)).containsEntry("status", "RELEASED");
        assertThat(assignmentsOf("A-REPL-1")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("execution_id", "exec_E2"));
        assertThat(outboxCount("ExecutionsReleased", "A-REPL-1")).isEqualTo(1);
        assertThat(outboxCount("ExecutionsAllocated", "A-REPL-1")).isEqualTo(1);
        assertThat(outboxCount("SettlementRequested", "A-REPL-1")).isEqualTo(1);
    }

    @Test
    void newAllocationConflictAtAllocateStepIsRejectedNotStuck() {
        Long id = persistNew("A-CONFLICT-1", "100.0000", List.of("E1"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E1", "exec_E1", "100.0000", "2026-07-17T10:00:00Z");
        stubAllocateConflict(); // trading returns 409 on ALLOCATE (loser of a race)

        driver.runOnce();

        Allocation a = reload(id);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.REJECTED); // VALIDATED -> REJECTED, legal
        assertThat(a.getFailureCategory()).isEqualTo(OrchestrationFailureCategory.BUSINESS_REJECTION);
        assertThat(assignmentsOf("A-CONFLICT-1")).isEmpty();
    }

    @Test
    void replaceFailingAfterReleaseReachesALegalTerminalAndRetainsOriginalHistory() {
        Long originalId = persistNew("A-ORIG-3", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");
        driver.runOnce();
        assertThat(reload(originalId).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        Long replaceId = persistAllocation("A-REPL-2", AllocTransType.REPLACE, "A-ORIG-3", "AAPL", "1",
                "100.0000", List.of(new AccountQty("ACC-1", "100.0000")), List.of("E2"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E2", "exec_E2", "100.0000", "2026-07-17T11:00:00Z");
        stubReleaseOkAllocateConflict(); // release commits EXECUTIONS_RELEASED, then ALLOCATE 409s

        driver.runOnce();

        // Before the fix this threw IllegalAllocationTransitionException (EXECUTIONS_RELEASED -> REJECTED)
        // and left the replacement stuck non-terminal. It must now reach a legal terminal.
        assertThat(reload(replaceId).getStatus()).isEqualTo(AllocationStatus.REJECTED);
        // The original's assignment history is retained (released, never deleted).
        assertThat(assignmentsOf("A-ORIG-3")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("status", "RELEASED"));
    }

    @Test
    void permanentDependencyFailureEscalatesToFailedWithoutRetry() {
        Long id = persistNew("A-PERM-1", "100.0000", List.of("E1"));
        stubAccountsStatus(403); // authorization/config failure — terminal, never retried

        driver.runOnce();

        Allocation a = reload(id);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.FAILED);
        assertThat(a.getNextAttemptAt()).isNull();
        assertThat(a.getFailureCategory().isRetryable()).isFalse();
    }

    @Test
    void resumesFromMidLifecycleStateWithoutDuplicatingEarlierEvents() {
        Long id = persistNew("A-RESUME-1", "100.0000", List.of("E1"));
        // Simulate a crash after EXECUTIONS_ALLOCATED committed (assignment + one ExecutionsAllocated
        // event already persisted) but before COMPLETED.
        jdbc.update("UPDATE allocations SET status = 'EXECUTIONS_ALLOCATED' WHERE id = ?", id);
        jdbc.update("""
                INSERT INTO allocation_assignments (allocation_id, execution_id, allocated_qty,
                    released_qty, status, assigned_at, created_at, updated_at)
                VALUES (?, 'exec_E1', '100.0000', 0, 'ASSIGNED', NOW(6), NOW(6), NOW(6))
                """, id);
        jdbc.update("""
                INSERT INTO outbox_event (event_id, event_type, aggregate_type, aggregate_id, topic,
                    message_key, payload, correlation_id, attempts, created_at)
                VALUES ('pre-resume-1', 'ExecutionsAllocated', 'ALLOCATION', 'A-RESUME-1',
                    'tcs.allocation.executions-allocated.v1', 'A-RESUME-1', x'00', 'cid-A-RESUME-1', 0, NOW(6))
                """);

        driver.runOnce();

        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.COMPLETED);
        assertThat(outboxCount("SettlementRequested", "A-RESUME-1")).isEqualTo(1);
        assertThat(outboxCount("ExecutionsAllocated", "A-RESUME-1")).isEqualTo(1); // not re-emitted
    }

    @Test
    void completionWritesTheRedisDedupMarker() {
        Long id = persistNew("A-DEDUP-1", "100.0000", List.of("E1"));
        stubAllOkFor("E1", "100.0000");
        driver.runOnce();
        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        // The read side is consulted on every process() pass; the completion marker is written through
        // the real Redis, so a re-submission short-circuits (a hint the DB then confirms as terminal).
        assertThat(dedupFilter.isLikelyProcessed("A-DEDUP-1:0")).isTrue();
        assertThat(orchestrator.process(id)).isEqualTo(OrchestrationOutcome.SKIPPED);
    }
}
