package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.client.DependencyCallException;
import com.tradingcapture.allocation.client.TradingExecutionClient;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.orchestration.OrchestrationReader.ReleaseTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Releases the executions of allocations that ended REJECTED or FAILED while trading-service may
 * still hold them bound to their allocId (see {@code Allocation#reject}/{@code failTerminally}).
 * Otherwise those executions would stay ALLOCATED to an allocation that never settles, and no later
 * allocation could take them.
 *
 * <p>The trading call runs outside any transaction, like every orchestration call. It keeps
 * retrying with the orchestration backoff, with no attempt limit: the release must happen
 * eventually, and every attempt is idempotent.</p>
 */
@Component
public class ExecutionReleaseDriver {

    private static final Logger log = LoggerFactory.getLogger(ExecutionReleaseDriver.class);

    private final AllocationRepository allocations;
    private final OrchestrationReader reader;
    private final TradingExecutionClient tradingClient;
    private final OrchestrationTx tx;
    private final OrchestrationProperties properties;
    private final OrchestrationMetrics metrics;
    private final Clock clock;

    public ExecutionReleaseDriver(AllocationRepository allocations, OrchestrationReader reader,
                                  TradingExecutionClient tradingClient, OrchestrationTx tx,
                                  OrchestrationProperties properties, OrchestrationMetrics metrics, Clock clock) {
        this.allocations = allocations;
        this.reader = reader;
        this.tradingClient = tradingClient;
        this.tx = tx;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Release one bounded batch of due allocations. Returns how many were released. */
    public int runOnce() {
        Instant now = clock.instant();
        List<Long> due = allocations.findDueExecutionReleases(now, properties.getBatchSize());
        int released = 0;
        for (Long id : due) {
            ReleaseTarget target = reader.releaseTarget(id).orElse(null);
            if (target == null) {
                continue;
            }
            try {
                tradingClient.release(target.allocId(), null);
            } catch (DependencyCallException e) {
                Instant next = clock.instant().plus(properties.backoffFor(target.releaseAttemptCount() + 1));
                tx.recordExecutionReleaseFailure(id, e.getMessage(), next);
                metrics.executionRelease("retry");
                log.warn("Releasing executions of allocId={} failed (attempt {}), retrying at {}: {}",
                        target.allocId(), target.releaseAttemptCount() + 1, next, e.getMessage());
                continue;
            }
            if (tx.commitExecutionRelease(id)) {
                released++;
                metrics.executionRelease("released");
                log.info("Released executions bound to unsuccessful allocation allocId={}", target.allocId());
            }
        }
        return released;
    }
}
