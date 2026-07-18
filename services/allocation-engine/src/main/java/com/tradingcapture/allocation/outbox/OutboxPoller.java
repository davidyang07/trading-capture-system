package com.tradingcapture.allocation.outbox;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.allocation.observability.IngestionMetrics;
import com.tradingcapture.events.EventHeaders;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polling publisher for the transactional outbox (ADR-0005): loads unpublished rows in id
 * order, publishes each through the Confluent Avro producer ({@code acks=all}, idempotent),
 * and stamps {@code published_at} after the broker acknowledges. A publish failure stops the
 * batch — never skips a row — so per-aggregate ordering is preserved; delivery is therefore
 * at-least-once and consumers deduplicate on {@code EventMetadata.eventId}. A crash between
 * broker ack and the stamp only causes a duplicate publication, never a loss.
 */
@Component
@ConditionalOnProperty(name = "tcs.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository outboxEvents;
    private final AvroPayloadCodec codec;
    private final KafkaTemplate<String, SpecificRecord> avroKafkaTemplate;
    private final IngestionMetrics metrics;
    private final Clock clock;
    private final int batchSize;
    private final Duration publishTimeout;

    public OutboxPoller(OutboxEventRepository outboxEvents,
                        AvroPayloadCodec codec,
                        @Qualifier("avroKafkaTemplate") KafkaTemplate<String, SpecificRecord> avroKafkaTemplate,
                        IngestionMetrics metrics,
                        Clock clock,
                        @Value("${tcs.outbox.batch-size:50}") int batchSize,
                        @Value("${tcs.outbox.publish-timeout:10s}") Duration publishTimeout) {
        this.outboxEvents = outboxEvents;
        this.codec = codec;
        this.avroKafkaTemplate = avroKafkaTemplate;
        this.metrics = metrics;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(fixedDelayString = "${tcs.outbox.poll-interval:500ms}")
    public void publishPending() {
        List<OutboxEvent> batch = outboxEvents.findByPublishedAtIsNullOrderByIdAsc(Limit.of(batchSize));
        for (OutboxEvent row : batch) {
            try {
                publish(row);
                row.markPublished(clock.instant());
                outboxEvents.save(row);
                metrics.outboxPublished();
            } catch (Exception e) {
                // Stop the batch to preserve id order; the next tick retries from this row.
                row.recordFailedAttempt(safeError(e));
                outboxEvents.save(row);
                metrics.outboxPublishFailed();
                log.warn("outbox publish failed; will retry [outboxId={} topic={} attempts={}]: {}",
                        row.getId(), row.getTopic(), row.getAttempts(), safeError(e));
                return;
            }
        }
    }

    private void publish(OutboxEvent row) throws Exception {
        SpecificRecord event = codec.decode(row.getEventType(), row.getPayload());
        ProducerRecord<String, SpecificRecord> producerRecord =
                new ProducerRecord<>(row.getTopic(), row.getMessageKey(), event);
        producerRecord.headers()
                .add(EventHeaders.EVENT_ID, bytes(row.getEventId()))
                .add(EventHeaders.CORRELATION_ID, bytes(row.getCorrelationId()));
        if (row.getCausationId() != null) {
            producerRecord.headers().add(EventHeaders.CAUSATION_ID, bytes(row.getCausationId()));
        }
        avroKafkaTemplate.send(producerRecord).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** Error summaries persist to last_error and logs: short, no stack traces, no payloads. */
    private static String safeError(Exception e) {
        String message = e.getMessage() == null ? "" : ": " + e.getMessage();
        String summary = e.getClass().getSimpleName() + message;
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }
}
