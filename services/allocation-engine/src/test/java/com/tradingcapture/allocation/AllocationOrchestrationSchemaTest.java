package com.tradingcapture.allocation;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the V2 orchestration schema against real MySQL: the new tables/columns validate under
 * {@code ddl-auto=validate}, the assignment/failure-category/transition CHECK and UNIQUE
 * constraints behave (not merely exist by name), the aggregate round-trips its append-only
 * history + assignments, released assignments are retained (not deleted), and optimistic locking
 * rejects a stale concurrent update.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class AllocationOrchestrationSchemaTest extends MySqlContainerSupport {

    private static final Instant T1 = Instant.parse("2026-07-17T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-07-17T00:00:02Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AllocationRepository allocations;

    @Autowired
    private TestEntityManager em;

    // --- schema shape --------------------------------------------------------------------------

    @Test
    void v2CreatedTheOrchestrationTablesAndColumns() {
        assertThat(jdbc.queryForList("SHOW TABLES", String.class))
                .contains("allocation_state_transitions", "allocation_assignments");

        Integer newColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'allocations'
                  AND column_name IN ('attempt_count','max_attempts','next_attempt_at',
                                      'completed_at','failure_category','failure_summary',
                                      'claimed_by','claimed_at')
                """, Integer.class);
        assertThat(newColumns).isEqualTo(8);
    }

    // --- constraint behavior (native inserts) --------------------------------------------------

    @Test
    void assignmentIsUniquePerAllocationAndExecution() {
        long allocationId = insertReceivedAllocation("ALLOC-UQ", "0");
        jdbc.update(assignmentInsert(), allocationId, "EXEC-1", "100.0000");

        assertThatThrownBy(() -> jdbc.update(assignmentInsert(), allocationId, "EXEC-1", "50.0000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_alloc_assignments_execution");
    }

    @Test
    void allocatedQuantityMustBePositive() {
        long allocationId = insertReceivedAllocation("ALLOC-POS", "0");
        assertThatThrownBy(() -> jdbc.update(assignmentInsert(), allocationId, "EXEC-2", "0.0000"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_alloc_assignments_allocated_qty");
    }

    @Test
    void releasedQuantityCannotBeNegativeOrExceedAllocated() {
        long allocationId = insertReceivedAllocation("ALLOC-REL", "0");
        String insertWithReleased = """
                INSERT INTO allocation_assignments (allocation_id, execution_id, allocated_qty,
                    released_qty, status, assigned_at, created_at, updated_at)
                VALUES (?, ?, '100.0000', ?, 'ASSIGNED', NOW(6), NOW(6), NOW(6))
                """;
        assertThatThrownBy(() -> jdbc.update(insertWithReleased, allocationId, "EXEC-NEG", "-1.0000"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_alloc_assignments_released_qty");
        assertThatThrownBy(() -> jdbc.update(insertWithReleased, allocationId, "EXEC-OVER", "100.0001"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_alloc_assignments_released_qty");
    }

    @Test
    void failureCategoryCheckRejectsUnknownCode() {
        long allocationId = insertReceivedAllocation("ALLOC-FC", "0");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE allocations SET failure_category = 'SOMETHING_ELSE' WHERE id = ?", allocationId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_allocations_failure_category");
    }

    @Test
    void transitionSeqIsUniquePerAllocation() {
        long allocationId = insertReceivedAllocation("ALLOC-SEQ", "0");
        String insert = """
                INSERT INTO allocation_state_transitions (allocation_id, seq, from_status,
                    to_status, occurred_at, created_at)
                VALUES (?, 1, 'RECEIVED', 'VALIDATED', NOW(6), NOW(6))
                """;
        jdbc.update(insert, allocationId);
        assertThatThrownBy(() -> jdbc.update(insert, allocationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_alloc_transitions_seq");
    }

    // --- aggregate round-trip ------------------------------------------------------------------

    @Test
    void aggregatePersistsHistoryAndAssignmentsAndRetainsThemAcrossRelease() {
        long rawId = insertRawMessage("t-orch-rt", 100L);
        Allocation allocation = new Allocation("ALLOC-RT", AllocTransType.NEW,
                AllocationStatus.RECEIVED, rawId, T1);
        allocation.recordAttempt(T1);
        allocation.transitionTo(AllocationStatus.VALIDATED, "logical validation passed", T1);
        allocation.assignExecution("EXEC-A", new BigDecimal("100.0000"), T1);
        allocation.transitionTo(AllocationStatus.EXECUTIONS_ALLOCATED, T1);
        Long id = allocations.saveAndFlush(allocation).getId();
        em.clear();

        Allocation reloaded = allocations.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AllocationStatus.EXECUTIONS_ALLOCATED);
        assertThat(reloaded.getTransitions()).hasSize(2);
        assertThat(reloaded.getTransitions().get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(reloaded.getAssignments()).hasSize(1);
        assertThat(reloaded.netAllocatedQuantity()).isEqualByComparingTo("100.0000");

        // Release the executions and complete: history + released assignment are RETAINED.
        reloaded.transitionTo(AllocationStatus.EXECUTIONS_RELEASED, T2);
        reloaded.releaseAllAssignments(T2);
        allocations.saveAndFlush(reloaded);
        em.clear();

        Allocation afterRelease = allocations.findById(id).orElseThrow();
        assertThat(afterRelease.getTransitions()).hasSize(3);
        assertThat(afterRelease.getAssignments()).hasSize(1);
        assertThat(afterRelease.getAssignments().get(0).isReleased()).isTrue();
        assertThat(afterRelease.netAllocatedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void optimisticLockingRejectsAStaleConcurrentUpdate() {
        long rawId = insertRawMessage("t-orch-ol", 200L);
        Long id = allocations.saveAndFlush(new Allocation("ALLOC-OL", AllocTransType.NEW,
                AllocationStatus.RECEIVED, rawId, T1)).getId();
        em.flush();
        em.clear();

        Allocation first = allocations.findById(id).orElseThrow();
        em.clear();
        Allocation second = allocations.findById(id).orElseThrow();
        em.clear();

        first.claim("worker-a", T1);
        allocations.saveAndFlush(first);
        em.clear();

        second.claim("worker-b", T2);
        assertThatThrownBy(() -> allocations.saveAndFlush(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    // --- helpers -------------------------------------------------------------------------------

    private long insertRawMessage(String topic, long offset) {
        jdbc.update("""
                INSERT INTO raw_messages (topic, kafka_partition, kafka_offset, payload,
                    correlation_id, status, delivery_count, received_at, created_at, updated_at)
                VALUES (?, 0, ?, 'payload', 'corr', 'RECEIVED', 1, NOW(6), NOW(6), NOW(6))
                """, topic, offset);
        return jdbc.queryForObject(
                "SELECT id FROM raw_messages WHERE topic = ?", Long.class, topic);
    }

    private long insertReceivedAllocation(String allocId, String transType) {
        long rawId = insertRawMessage("t-raw-" + allocId, allocId.hashCode() & 0x7fffffffL);
        jdbc.update("""
                INSERT INTO allocations (alloc_id, alloc_trans_type, status, raw_message_id,
                    created_at, updated_at, version)
                VALUES (?, ?, 'RECEIVED', ?, NOW(6), NOW(6), 0)
                """, allocId, transType, rawId);
        return jdbc.queryForObject(
                "SELECT id FROM allocations WHERE alloc_id = ?", Long.class, allocId);
    }

    private static String assignmentInsert() {
        return """
                INSERT INTO allocation_assignments (allocation_id, execution_id, allocated_qty,
                    status, assigned_at, created_at, updated_at)
                VALUES (?, ?, ?, 'ASSIGNED', NOW(6), NOW(6), NOW(6))
                """;
    }
}
