package com.tradingcapture.allocation.outbox;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.allocation.observability.IngestionMetrics;
import com.tradingcapture.events.EventHeaders;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
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
 *
 * <p>The exception is a row that can <em>never</em> be published: an undecodable payload, a
 * record over the broker's size limit, or a schema the registry refuses as incompatible or
 * invalid. Retrying it would block every later event forever, so it is parked
 * ({@code parked_at}, the {@code tcs.allocation.outbox.parked} gauge, an ERROR log) and the batch
 * moves on. Its aggregate's later events then publish without it; an operator must deal with
 * the parked row. Anything else, including an unreachable broker or registry, is retried in
 * order as before.</p>
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
        List<OutboxEvent> batch = outboxEvents.findByPublishedAtIsNullAndParkedAtIsNullOrderByIdAsc(
                Limit.of(batchSize));
        for (OutboxEvent row : batch) {
            try {
                publish(row);
                row.markPublished(clock.instant());
                outboxEvents.save(row);
                metrics.outboxPublished();
            } catch (Exception e) {
                if (isUnpublishable(e)) {
                    row.park(safeError(e), clock.instant());
                    outboxEvents.save(row);
                    metrics.outboxParked();
                    log.error("outbox event can never be published; parked so later events are not blocked "
                                    + "[outboxId={} eventType={} topic={} key={}]: {}",
                            row.getId(), row.getEventType(), row.getTopic(), row.getMessageKey(), safeError(e));
                    continue;
                }
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
        SpecificRecord event;
        try {
            event = codec.decode(row.getEventType(), row.getPayload());
        } catch (RuntimeException e) {
            throw new UnpublishableEventException("undecodable payload: " + e.getMessage(), e);
        }
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

    /** True for failures that no retry can fix (see the class comment). */
    static boolean isUnpublishable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnpublishableEventException
                    || cause instanceof RecordTooLargeException
                    || cause instanceof RecordBatchTooLargeException
                    || cause instanceof InvalidTopicException) {
                return true;
            }
            // 409: incompatible with the registered schema; 422: not a valid schema.
            if (cause instanceof RestClientException registry
                    && (registry.getStatus() == 409 || registry.getStatus() == 422)) {
                return true;
            }
        }
        return false;
    }

    private static final class UnpublishableEventException extends RuntimeException {
        UnpublishableEventException(String message, Throwable cause) {
            super(message, cause);
        }
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
