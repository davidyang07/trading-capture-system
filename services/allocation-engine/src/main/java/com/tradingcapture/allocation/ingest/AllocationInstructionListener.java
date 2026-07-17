package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.domain.model.RawMessage;
import com.tradingcapture.allocation.observability.IngestionMetrics;
import com.tradingcapture.events.EventHeaders;
import com.tradingcapture.events.EventTopics;
import com.tradingcapture.observability.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Inbound FIX consumer with non-blocking retries: transient failures redeliver through
 * {@code -retry-0/1/2} (1s → 4s → 16s, 4 attempts total) and exhaust onto {@code -dlt};
 * permanent failures are handled to a terminal state inside the pipeline and acknowledged
 * (never retried) — docs/architecture/event-flows.md.
 *
 * <p>Offsets commit only after the listener returns, i.e. after the durable work of the
 * attempt is complete; a crash before that point redelivers. Records arriving from a retry
 * hop keep their ORIGINAL Kafka coordinates (spring-kafka's {@code kafka_original-*}
 * headers), so every hop maps onto the same raw_messages row.</p>
 *
 * <p>Non-blocking retries relax strict per-key ordering while a record waits on a retry
 * topic — an accepted trade-off: processing is idempotent and the Phase 8 state machine
 * validates references logically, not positionally.</p>
 */
@Component
public class AllocationInstructionListener {

    private static final Logger log = LoggerFactory.getLogger(AllocationInstructionListener.class);

    private final RawMessageService rawMessageService;
    private final AllocationIngestionService ingestionService;
    private final IngestionMetrics metrics;

    public AllocationInstructionListener(RawMessageService rawMessageService,
                                         AllocationIngestionService ingestionService,
                                         IngestionMetrics metrics) {
        this.rawMessageService = rawMessageService;
        this.ingestionService = ingestionService;
        this.metrics = metrics;
    }

    @RetryableTopic(
            // Defaults per the catalog: 4 attempts, 1s → 4s → 16s. Property-driven so
            // integration tests can exercise real exhaustion without multi-second waits.
            attempts = "${tcs.ingest.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${tcs.ingest.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${tcs.ingest.retry.multiplier:4.0}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = EventTopics.RETRY_SUFFIX,
            dltTopicSuffix = EventTopics.DLT_SUFFIX,
            include = TransientIngestionException.class,
            kafkaTemplate = "stringKafkaTemplate")
    @KafkaListener(topics = "${tcs.ingest.inbound-topic:" + EventTopics.FIX_ALLOCATION_INSTRUCTION + "}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        metrics.consumed();
        if (record.topic().contains(EventTopics.RETRY_SUFFIX + "-")) {
            metrics.retriedDelivery();
        }
        String correlationId = correlationId(record);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            if (record.value() == null) {
                // ErrorHandlingDeserializer left no payload to persist or parse — nothing
                // durable can be done; acknowledge and surface via log + metric.
                metrics.parseFailed();
                log.error("undeserializable inbound record acknowledged [topic={} partition={} offset={}]",
                        record.topic(), record.partition(), record.offset());
                return;
            }
            Coordinates coordinates = Coordinates.originalOf(record);
            IngestionOutcome outcome = ingest(record, coordinates, correlationId);
            count(outcome);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private IngestionOutcome ingest(ConsumerRecord<String, String> record,
                                    Coordinates coordinates, String correlationId) {
        try {
            RawMessage raw = rawMessageService.recordArrival(
                    coordinates.topic(), coordinates.partition(), coordinates.offset(),
                    record.key(), record.value(), correlationId);
            if (raw.getStatus().isTerminal()) {
                return IngestionOutcome.ALREADY_PROCESSED;
            }
            return ingestionService.process(raw.getId());
        } catch (TransientIngestionException e) {
            throw e;
        } catch (RuntimeException e) {
            // Anything unexpected gets bounded retries and then the DLT — one bad message
            // must never stall the partition or crash the consumer permanently.
            throw new TransientIngestionException(
                    "Ingestion attempt failed for " + coordinates.describe(), e);
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        metrics.deadLettered();
        Coordinates coordinates = Coordinates.originalOf(record);
        String correlationId = correlationId(record);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            log.error("message exhausted transient retries; recording FAILED [{}]", coordinates.describe());
            ingestionService.recordRetryExhaustion(
                    coordinates.topic(), coordinates.partition(), coordinates.offset(),
                    record.key(), record.value(), correlationId,
                    "transient processing failures exhausted bounded retries");
        } catch (RuntimeException e) {
            // Never rethrow here: the record stays on the DLT for manual replay either way.
            log.error("DLT bookkeeping failed; record remains on {} for replay [{}]",
                    record.topic(), coordinates.describe(), e);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private void count(IngestionOutcome outcome) {
        switch (outcome) {
            case ACCEPTED -> metrics.accepted();
            case INVALID -> metrics.rejected();
            case PARSE_FAILED -> metrics.parseFailed();
            case UNSUPPORTED_TYPE -> metrics.unsupportedType();
            case DUPLICATE_BUSINESS -> metrics.duplicateBusiness();
            case ALREADY_PROCESSED -> metrics.duplicateDelivery();
        }
    }

    /**
     * Shape a correlation id must have to be accepted from the (unauthenticated) inbound
     * topic: anything else — control characters, log-forging payloads, values that would
     * overflow the VARCHAR(64) audit column — is replaced by a fresh UUID.
     */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Correlation ID from the upstream header, or a fresh one when absent or unsafe. */
    static String correlationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(EventHeaders.CORRELATION_ID);
        if (header == null || header.value() == null || header.value().length == 0) {
            return UUID.randomUUID().toString();
        }
        String candidate = new String(header.value(), StandardCharsets.UTF_8);
        return SAFE_CORRELATION_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    /**
     * The record's ORIGINAL coordinates: for retry/DLT hops spring-kafka carries them in
     * the {@code kafka_original-*} headers; on the main topic they are the record's own.
     */
    private record Coordinates(String topic, int partition, long offset) {

        static Coordinates originalOf(ConsumerRecord<String, String> record) {
            Header topicHeader = record.headers().lastHeader(KafkaHeaders.ORIGINAL_TOPIC);
            Header partitionHeader = record.headers().lastHeader(KafkaHeaders.ORIGINAL_PARTITION);
            Header offsetHeader = record.headers().lastHeader(KafkaHeaders.ORIGINAL_OFFSET);
            if (topicHeader == null || partitionHeader == null || offsetHeader == null) {
                return new Coordinates(record.topic(), record.partition(), record.offset());
            }
            return new Coordinates(
                    new String(topicHeader.value(), StandardCharsets.UTF_8),
                    ByteBuffer.wrap(partitionHeader.value()).getInt(),
                    ByteBuffer.wrap(offsetHeader.value()).getLong());
        }

        String describe() {
            return topic + "-" + partition + "@" + offset;
        }
    }
}
