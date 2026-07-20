package com.tradingcapture.allocation.orchestration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11: guards the {@code tcs.allocation.outbox.backlog} gauge so a renamed meter, a wrong
 * repository method, or an inverted {@code published_at IS NULL} predicate can't silently break the
 * Grafana backlog panel with green tests. Uses the real repository against the container DB.
 */
class OutboxBacklogGaugeIntegrationTest extends OrchestrationIntegrationTestSupport {

    @Autowired
    private MeterRegistry registry;

    private double backlog() {
        return registry.get("tcs.allocation.outbox.backlog").gauge().value();
    }

    private void insertOutboxRow(boolean published) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outbox_event (event_id, event_type, aggregate_type, aggregate_id, topic,
                    message_key, payload, correlation_id, attempts, created_at, published_at)
                VALUES (?, 'AllocationReceived', 'ALLOCATION', ?, 't', ?, ?, ?, 0, NOW(6), %s)
                """.formatted(published ? "NOW(6)" : "NULL"),
                id, id, id, new byte[]{1}, "cid-" + id);
    }

    @Test
    void gaugeReportsUnpublishedRowsAndTracksPublication() {
        // Base @BeforeEach wiped outbox_event → clean slate.
        assertThat(backlog()).isZero();

        insertOutboxRow(false);
        insertOutboxRow(false);
        insertOutboxRow(false);
        insertOutboxRow(true); // already published → must NOT count toward the backlog

        assertThat(backlog()).isEqualTo(3.0);

        // Publish one of the pending rows; the gauge must drop.
        jdbc.update("UPDATE outbox_event SET published_at = NOW(6) WHERE published_at IS NULL LIMIT 1");
        assertThat(backlog()).isEqualTo(2.0);
    }
}
