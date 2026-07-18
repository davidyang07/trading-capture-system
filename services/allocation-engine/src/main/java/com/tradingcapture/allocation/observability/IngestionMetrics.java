package com.tradingcapture.allocation.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for the ingestion pipeline (naming style follows the other services'
 * {@code tcs.<service>.*} conventions). Backlog gauges and dashboards arrive in Phase 11.
 */
@Component
public class IngestionMetrics {

    private final Counter consumed;
    private final Counter accepted;
    private final Counter rejected;
    private final Counter parseFailed;
    private final Counter unsupportedType;
    private final Counter duplicateDelivery;
    private final Counter duplicateBusiness;
    private final Counter retriedDelivery;
    private final Counter deadLettered;
    private final Counter outboxPublished;
    private final Counter outboxPublishFailed;

    public IngestionMetrics(MeterRegistry registry) {
        this.consumed = counter(registry, "consumed", "Inbound records consumed (all deliveries)");
        this.accepted = counter(registry, "accepted", "Allocations persisted RECEIVED");
        this.rejected = counter(registry, "rejected", "Messages rejected by field validation (INVALID)");
        this.parseFailed = counter(registry, "parse_failed", "Unparseable FIX payloads (PARSE_FAILED)");
        this.unsupportedType = counter(registry, "unsupported_type", "Messages with an unsupported MsgType");
        this.duplicateDelivery = counter(registry, "duplicate_delivery", "Broker redeliveries of already-terminal records");
        this.duplicateBusiness = counter(registry, "duplicate_business", "Business duplicates (same AllocID + AllocTransType)");
        this.retriedDelivery = counter(registry, "retried_delivery", "Records consumed from a retry topic");
        this.deadLettered = counter(registry, "dead_lettered", "Records that exhausted retries onto the DLT");
        this.outboxPublished = counter(registry, "outbox_published", "Outbox events published to Kafka");
        this.outboxPublishFailed = counter(registry, "outbox_publish_failed", "Outbox publish attempts that failed");
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder("tcs.allocation.ingest." + name).description(description).register(registry);
    }

    public void consumed() {
        consumed.increment();
    }

    public void accepted() {
        accepted.increment();
    }

    public void rejected() {
        rejected.increment();
    }

    public void parseFailed() {
        parseFailed.increment();
    }

    public void unsupportedType() {
        unsupportedType.increment();
    }

    public void duplicateDelivery() {
        duplicateDelivery.increment();
    }

    public void duplicateBusiness() {
        duplicateBusiness.increment();
    }

    public void retriedDelivery() {
        retriedDelivery.increment();
    }

    public void deadLettered() {
        deadLettered.increment();
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxPublishFailed() {
        outboxPublishFailed.increment();
    }
}
