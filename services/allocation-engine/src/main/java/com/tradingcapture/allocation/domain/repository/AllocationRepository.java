package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    /** Business-duplicate lookup on the unique (allocId, allocTransType) key (ADR-0007). */
    Optional<Allocation> findByAllocIdAndAllocTransType(String allocId, AllocTransType allocTransType);

    List<Allocation> findByAllocIdAndAllocTransTypeInOrderByIdDesc(String allocId,
                                                                   Collection<AllocTransType> types);

    /**
     * The allocation a Replace/Cancel's RefAllocID (72) refers to: the New <em>or Replace</em>
     * instruction with that AllocID (so a chain can continue from a replacement), other than the
     * referencing instruction itself. Prefers the one still live ({@link
     * com.tradingcapture.allocation.domain.model.AllocationStatus#isSupersedable()}), otherwise the
     * most recent, so the caller can report why it is ineligible (e.g. already REPLACED).
     */
    default Optional<Allocation> findReferencedOriginal(String refAllocId, Long referencingId) {
        List<Allocation> candidates = findByAllocIdAndAllocTransTypeInOrderByIdDesc(
                refAllocId, EnumSet.of(AllocTransType.NEW, AllocTransType.REPLACE)).stream()
                .filter(candidate -> !candidate.getId().equals(referencingId))
                .toList();
        return candidates.stream()
                .filter(candidate -> candidate.getStatus().isSupersedable())
                .findFirst()
                .or(() -> candidates.stream().findFirst());
    }

    /**
     * Recovery/orchestration driver work selection (Increment 10). Returns ids of non-terminal
     * allocations that are due for their next attempt and not actively leased, locking the rows with
     * {@code FOR UPDATE SKIP LOCKED} so concurrent workers claim disjoint batches. Deterministic id
     * order. Must run inside the short claim transaction.
     */
    @Query(value = """
            SELECT id FROM allocations
            WHERE status IN ('RECEIVED','VALIDATED','EXECUTIONS_ALLOCATED','EXECUTIONS_RELEASED')
              AND attempt_count < max_attempts
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (claimed_at IS NULL OR claimed_at <= :leaseCutoff)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> claimEligibleWork(@Param("now") Instant now,
                                 @Param("leaseCutoff") Instant leaseCutoff,
                                 @Param("batchSize") int batchSize);

    /**
     * Non-terminal allocations that used up every attempt without reaching a terminal state and are
     * not actively leased — e.g. the last attempt crashed, hit a concurrency conflict, or raised an
     * unexpected error, none of which records a retry/terminal outcome. {@link #claimEligibleWork}
     * never selects them again, so the claim transaction fails them terminally instead of leaving
     * them stranded. Locked {@code FOR UPDATE SKIP LOCKED} like the claim itself.
     */
    @Query(value = """
            SELECT id FROM allocations
            WHERE status IN ('RECEIVED','VALIDATED','EXECUTIONS_ALLOCATED','EXECUTIONS_RELEASED')
              AND attempt_count >= max_attempts
              AND (claimed_at IS NULL OR claimed_at <= :leaseCutoff)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findStrandedExhaustedWork(@Param("leaseCutoff") Instant leaseCutoff,
                                         @Param("batchSize") int batchSize);

    /**
     * Allocations that ended REJECTED/FAILED while trading-service may still hold executions bound
     * to their allocId, and whose next release attempt is due. Releasing is idempotent, so two
     * workers picking the same row only repeat a no-op.
     */
    @Query(value = """
            SELECT id FROM allocations
            WHERE executions_release_pending = TRUE
              AND (next_release_at IS NULL OR next_release_at <= :now)
            ORDER BY id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findDueExecutionReleases(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
