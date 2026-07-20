package com.tradingcapture.allocation.observability;

import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Phase 11 (ADR-0015): publishes {@code tcs.allocation.outbox.backlog} — the number of
 * unpublished outbox rows — as a Micrometer gauge. A sustained non-zero backlog means the
 * {@code OutboxPoller} is falling behind or failing to reach Kafka, which the Grafana
 * allocation dashboard alerts on.
 *
 * <p>The gauge reads the count lazily on each scrape via the repository; the value is a
 * cheap indexed {@code COUNT(*) WHERE published_at IS NULL}.</p>
 */
@Component
public class OutboxBacklogGauge {

    public OutboxBacklogGauge(MeterRegistry registry, OutboxEventRepository outbox) {
        Gauge.builder("tcs.allocation.outbox.backlog", outbox, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Unpublished allocation outbox rows awaiting Kafka publication")
                .register(registry);
    }
}
