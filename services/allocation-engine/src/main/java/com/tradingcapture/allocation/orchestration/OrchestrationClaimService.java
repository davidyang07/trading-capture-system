package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The short claim transaction of the recovery/orchestration driver. Selects a bounded, deterministic
 * batch of eligible allocations with {@code FOR UPDATE SKIP LOCKED} (so concurrent workers claim
 * disjoint rows), stamps the worker lease and increments the attempt counter, and commits — all
 * before any remote calls are made. An abandoned claim becomes reclaimable once its lease expires.
 */
@Component
public class OrchestrationClaimService {

    private final AllocationRepository allocations;
    private final OrchestrationMetrics metrics;

    public OrchestrationClaimService(AllocationRepository allocations, OrchestrationMetrics metrics) {
        this.allocations = allocations;
        this.metrics = metrics;
    }

    @Transactional
    public List<Long> claim(String worker, Instant now, Instant leaseCutoff, int batchSize) {
        List<Long> ids = allocations.claimEligibleWork(now, leaseCutoff, batchSize);
        int reclaimed = 0;
        for (Long id : ids) {
            var allocation = allocations.findById(id).orElse(null);
            if (allocation == null) {
                continue;
            }
            // The query only returns rows whose lease is absent or expired, so a non-null prior
            // claim means we are reclaiming abandoned work.
            if (allocation.getClaimedAt() != null) {
                reclaimed++;
            }
            allocation.claim(worker, now);
            allocation.recordAttempt(now);
        }
        metrics.driver("scanned", ids.size());
        metrics.driver("claimed", ids.size());
        if (reclaimed > 0) {
            metrics.driver("lease_reclaimed", reclaimed);
        }
        return ids;
    }
}
