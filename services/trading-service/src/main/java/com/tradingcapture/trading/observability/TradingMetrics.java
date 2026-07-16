package com.tradingcapture.trading.observability;

import com.tradingcapture.trading.domain.model.ExecutionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Trading-domain Micrometer counters. Registered now, scraped once /actuator/prometheus is
 * exposed in Phase 11 (ADR-0015); until then they are still visible via the metrics endpoint
 * in local diagnostics.
 */
@Component
public class TradingMetrics {

    private final Counter ordersCreated;
    private final Counter ordersUpdated;
    private final Counter ordersDeleted;
    private final MeterRegistry registry;

    public TradingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ordersCreated = registry.counter("tcs.trading.orders.created");
        this.ordersUpdated = registry.counter("tcs.trading.orders.updated");
        this.ordersDeleted = registry.counter("tcs.trading.orders.deleted");
    }

    public void orderCreated() {
        ordersCreated.increment();
    }

    public void orderUpdated() {
        ordersUpdated.increment();
    }

    public void orderDeleted() {
        ordersDeleted.increment();
    }

    public void executionRecorded(ExecutionType execType) {
        registry.counter("tcs.trading.executions.recorded", "execType", execType.name()).increment();
    }

    public void allocationStateApplied(String action, long applied) {
        registry.counter("tcs.trading.allocation_state.applied", "action", action).increment(applied);
    }
}
