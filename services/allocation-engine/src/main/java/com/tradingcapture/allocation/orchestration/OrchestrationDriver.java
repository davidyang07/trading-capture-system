package com.tradingcapture.allocation.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The single scheduled driver for both normal orchestration and crash recovery (ADR-0006). Each
 * tick claims a bounded batch of eligible allocations (new work, due retries, or abandoned leases)
 * in a short transaction, then advances each one via {@link AllocationOrchestrator} with remote
 * calls made outside that transaction. There is no separate recovery job — resuming from persisted
 * state <em>is</em> the normal path. Disabled by default in tests, which call {@link #runOnce()}
 * directly for determinism.
 */
@Component
public class OrchestrationDriver {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationDriver.class);

    private final OrchestrationClaimService claimService;
    private final AllocationOrchestrator orchestrator;
    private final ExecutionReleaseDriver releaseDriver;
    private final OrchestrationProperties properties;
    private final OrchestrationMetrics metrics;
    private final Clock clock;
    private final String workerId = "alloc-" + UUID.randomUUID().toString().substring(0, 8);

    public OrchestrationDriver(OrchestrationClaimService claimService, AllocationOrchestrator orchestrator,
                               ExecutionReleaseDriver releaseDriver, OrchestrationProperties properties,
                               OrchestrationMetrics metrics, Clock clock) {
        this.claimService = claimService;
        this.orchestrator = orchestrator;
        this.releaseDriver = releaseDriver;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tcs.orchestration.poll-interval:PT1S}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.warn("Orchestration driver tick failed: {}", e.toString());
        }
        try {
            releaseDriver.runOnce();
        } catch (RuntimeException e) {
            log.warn("Execution release tick failed: {}", e.toString());
        }
    }

    /** Claim and process one bounded batch. Returns the number of allocations claimed this pass. */
    public int runOnce() {
        Instant now = clock.instant();
        Instant leaseCutoff = now.minus(properties.getClaimLease());
        List<Long> claimed = claimService.claim(workerId, now, leaseCutoff, properties.getBatchSize());
        for (Long id : claimed) {
            try {
                orchestrator.process(id);
                metrics.driver("processed");
            } catch (RuntimeException e) {
                // A failed step already persisted its retry/terminal outcome; unexpected errors are
                // logged and the row is retried on a later tick (bounded by max attempts).
                log.warn("Orchestration of allocationId={} raised {}", id, e.toString());
            }
        }
        return claimed.size();
    }

    public String workerId() {
        return workerId;
    }
}
