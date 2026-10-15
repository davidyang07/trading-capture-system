package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The short claim transaction of the recovery/orchestration driver. Selects a bounded, deterministic
 * batch of eligible allocations with {@code FOR UPDATE SKIP LOCKED} (so concurrent workers claim
 * disjoint rows), stamps the worker lease and increments the attempt counter, and commits — all
 * before any remote calls are made. An abandoned claim becomes reclaimable once its lease expires;
 * an abandoned claim on the <em>last</em> attempt is failed terminally here, since it would
 * otherwise never be claimed again.
 */
@Component
public class OrchestrationClaimService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationClaimService.class);

    private final AllocationRepository allocations;
    private final TerminalOutcomeEvents outcomes;
    private final OrchestrationMetrics metrics;

    public OrchestrationClaimService(AllocationRepository allocations, TerminalOutcomeEvents outcomes,
                                     OrchestrationMetrics metrics) {
        this.allocations = allocations;
        this.outcomes = outcomes;
        this.metrics = metrics;
    }

    @Transactional
    public List<Long> claim(String worker, Instant now, Instant leaseCutoff, int batchSize) {
        failStrandedExhaustedWork(now, leaseCutoff, batchSize);
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

    private void failStrandedExhaustedWork(Instant now, Instant leaseCutoff, int batchSize) {
        List<Long> stranded = allocations.findStrandedExhaustedWork(leaseCutoff, batchSize);
        for (Long id : stranded) {
            var allocation = allocations.findById(id).orElse(null);
            if (allocation == null) {
                continue;
            }
            String lastFailure = allocation.getFailureSummary() != null
                    ? " (last failure: " + allocation.getFailureSummary() + ")"
                    : "";
            allocation.failTerminally(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION,
                    "attempts exhausted in " + allocation.getStatus() + " without a recorded outcome"
                            + lastFailure, now);
            allocation.releaseClaim(now);
            outcomes.failed(allocation);
            log.warn("Allocation allocationId={} allocId={} exhausted its attempts without a recorded "
                    + "outcome — failed terminally", id, allocation.getAllocId());
        }
        if (!stranded.isEmpty()) {
            metrics.driver("exhausted_failed", stranded.size());
        }
    }
}
