package com.tradingcapture.allocation.outbox;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.allocation.observability.IngestionMetrics;
import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.EventMetadata;
import com.tradingcapture.events.EventTopics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaProducerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The poller's central guarantee: a publish failure STOPS the batch (per-aggregate id order
 * is never broken by skipping), records the attempt with a safe error, and the next tick
 * retries from the failed row. The one exception is a row no retry can ever publish, which is
 * parked so it cannot block every later event.
 */
class OutboxPollerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate = mock(KafkaTemplate.class);
    private final AvroPayloadCodec codec = new AvroPayloadCodec();

    private OutboxPoller poller;
    private OutboxEvent first;
    private OutboxEvent second;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(repository, codec, kafkaTemplate,
                new IngestionMetrics(new SimpleMeterRegistry()),
                Clock.fixed(NOW, ZoneOffset.UTC), 50, Duration.ofSeconds(1));
        first = row("ALLOC-1", "evt-1");
        second = row("ALLOC-2", "evt-2");
        when(repository.findByPublishedAtIsNullAndParkedAtIsNullOrderByIdAsc(any(Limit.class)))
                .thenReturn(List.of(first, second));
    }

    private OutboxEvent row(String allocId, String eventId) {
        AllocationReceived event = AllocationReceived.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId(eventId).setEventType("AllocationReceived").setSchemaVersion(1)
                        .setOccurredAt(NOW).setCorrelationId("corr").setAggregateId(allocId)
                        .build())
                .setAllocId(allocId).setAllocTransType("0").setSide("1")
                .setQuantity(new BigDecimal("100.0000"))
                .setTradeDate(LocalDate.of(2026, 7, 15)).setRawMessageId(1L)
                .build();
        return new OutboxEvent(eventId, "AllocationReceived", "ALLOCATION", allocId,
                EventTopics.ALLOCATION_RECEIVED, allocId, codec.encode(event), "corr", null, NOW);
    }

    @Test
    void aPublishFailureStopsTheBatchAndRecordsTheAttempt() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker gone")));

        poller.publishPending();

        // First row: attempt recorded, not published; second row: untouched (order kept).
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker gone").doesNotContain("at com.");
        assertThat(first.isPublished()).isFalse();
        assertThat(second.getAttempts()).isZero();
        assertThat(second.isPublished()).isFalse();
        verify(repository).save(first);
        verify(repository, never()).save(second);

        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().key()).isEqualTo("ALLOC-1"); // never skipped ahead
    }

    @Test
    void successfulPublicationStampsRowsInIdOrder() {
        SendResult<String, SpecificRecord> result = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        poller.publishPending();

        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isTrue();
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(first);
        verify(repository).save(second);

        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(ProducerRecord::key)
                .containsExactly("ALLOC-1", "ALLOC-2");
    }

    // --- rows that can never be published are parked, not retried forever -----------------------

    @SuppressWarnings("unchecked")
    private void secondRowPublishes() {
        SendResult<String, SpecificRecord> ok = mock(SendResult.class);
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, SpecificRecord>>argThat(
                r -> r != null && "ALLOC-2".equals(r.key()))))
                .thenReturn(CompletableFuture.completedFuture(ok));
    }

    @Test
    void anUndecodablePayloadIsParkedAndTheNextRowStillPublishes() {
        OutboxEvent garbage = new OutboxEvent("evt-x", "AllocationReceived", "ALLOCATION", "ALLOC-X",
                EventTopics.ALLOCATION_RECEIVED, "ALLOC-X", "not avro".getBytes(StandardCharsets.UTF_8),
                "corr", null, NOW);
        when(repository.findByPublishedAtIsNullAndParkedAtIsNullOrderByIdAsc(any(Limit.class)))
                .thenReturn(List.of(garbage, second));
        secondRowPublishes();

        poller.publishPending();

        assertThat(garbage.isParked()).isTrue();
        assertThat(garbage.getParkedAt()).isEqualTo(NOW);
        assertThat(garbage.getLastError()).contains("undecodable payload");
        assertThat(second.isPublished()).isTrue();
    }

    @Test
    void aRecordOverTheBrokerSizeLimitIsParked() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, SpecificRecord>>argThat(
                r -> r != null && "ALLOC-1".equals(r.key()))))
                .thenReturn(CompletableFuture.failedFuture(new KafkaProducerException(null, "send failed",
                        new RecordTooLargeException("The message is 1500000 bytes"))));
        secondRowPublishes();

        poller.publishPending();

        assertThat(first.isParked()).isTrue();
        assertThat(first.isPublished()).isFalse();
        assertThat(second.isPublished()).isTrue();
    }

    @Test
    void aSchemaTheRegistryRefusesIsParked() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, SpecificRecord>>argThat(
                r -> r != null && "ALLOC-1".equals(r.key()))))
                .thenThrow(new SerializationException("Error registering Avro schema",
                        new RestClientException("Schema being registered is incompatible", 409, 409)));
        secondRowPublishes();

        poller.publishPending();

        assertThat(first.isParked()).isTrue();
        assertThat(second.isPublished()).isTrue();
    }

    @Test
    void anUnreachableSchemaRegistryIsRetriedInOrderNotParked() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new SerializationException("Error registering Avro schema",
                        new ConnectException("Connection refused")));

        poller.publishPending();

        assertThat(first.isParked()).isFalse();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.isPublished()).isFalse();
        assertThat(second.getAttempts()).isZero();
    }
}
