package com.tradingcapture.allocation.client;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for outbound dependency calls ({@code tcs.allocation.dep.calls}), tagged by
 * dependency, operation, and a low-cardinality outcome (success / a {@link DependencyFailureType}
 * outcome / circuit_open). Retry and circuit-breaker state metrics come from resilience4j-micrometer.
 * No high-cardinality identifiers (allocId, execId) are ever used as tags.
 */
@Component
public class DependencyMetrics {

    private final MeterRegistry registry;

    public DependencyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String dependency, String operation, String outcome) {
        Counter.builder("tcs.allocation.dep.calls")
                .description("Outbound dependency calls by outcome")
                .tag("dependency", dependency)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
