package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    /**
     * Business-duplicate lookup on the unique (allocId, allocTransType) key (ADR-0007); also the
     * original-allocation lookup for Replace/Cancel — the referenced New instruction is
     * {@code findByAllocIdAndAllocTransType(refAllocId, NEW)}.
     */
    Optional<Allocation> findByAllocIdAndAllocTransType(String allocId, AllocTransType allocTransType);

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
}
