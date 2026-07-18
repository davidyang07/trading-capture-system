package com.tradingcapture.allocation.orchestration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for orchestration and the recovery driver. All tags are low-cardinality
 * (step name, outcome, driver event) — never allocation/account/execution/correlation ids.
 */
@Component
public class OrchestrationMetrics {

    private final MeterRegistry registry;

    public OrchestrationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** step ∈ {validate, allocate, release, complete}; outcome ∈ OrchestrationOutcome (lowercased). */
    public void step(String step, OrchestrationOutcome outcome) {
        Counter.builder("tcs.allocation.orchestration.steps")
                .tag("step", step).tag("outcome", outcome.name().toLowerCase())
                .register(registry).increment();
    }

    /** event ∈ {scanned, claimed, processed, lease_reclaimed, conflict, skipped}. */
    public void driver(String event, long amount) {
        Counter.builder("tcs.allocation.orchestration.driver")
                .tag("event", event).register(registry).increment(amount);
    }

    public void driver(String event) {
        driver(event, 1);
    }

    /** result ∈ {hit, miss, error}. */
    public void dedup(String result) {
        Counter.builder("tcs.allocation.dedup")
                .tag("result", result).register(registry).increment();
    }
}
