package com.tradingcapture.allocation;

import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.FailureCategory;
import com.tradingcapture.allocation.domain.model.OutboxEvent;
import com.tradingcapture.allocation.domain.model.RawMessage;
import com.tradingcapture.allocation.domain.model.RawMessageStatus;
import com.tradingcapture.allocation.domain.model.ValidationError;
import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import com.tradingcapture.allocation.domain.repository.ValidationErrorRepository;
import com.tradingcapture.allocation.fix.FixTestMessages;
import com.tradingcapture.allocation.ingest.AllocationIngestionService;
import com.tradingcapture.allocation.ingest.IngestionOutcome;
import com.tradingcapture.allocation.ingest.RawMessageService;
import com.tradingcapture.allocation.testsupport.AllocationIntegrationTestSupport;
import com.tradingcapture.allocation.testsupport.KafkaTestConsumers;
import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.AllocationRejected;
import com.tradingcapture.events.EventHeaders;
import com.tradingcapture.events.EventTopics;
import com.tradingcapture.events.ExceptionReported;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end ingestion against real Kafka + MySQL (Testcontainers): consume → persist raw →
 * parse → validate → state → outbox → published Avro events. Covers brief scenarios 7 (all
 * errors stored), 8 (malformed → exception event) and 14 (duplicate delivery idempotent).
 */
@SpringBootTest
class IngestionPipelineIntegrationTest extends AllocationIntegrationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private RawMessageRepository rawMessages;
    @Autowired
    private AllocationRepository allocations;
    @Autowired
    private ValidationErrorRepository validationErrors;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private RawMessageService rawMessageService;
    @Autowired
    private AllocationIngestionService ingestionService;
    @Autowired
    @Qualifier("stringKafkaTemplate")
    private KafkaTemplate<String, String> producer;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Runs assertions on the lazy aggregate graph inside a read transaction. */
    private void inTransaction(Runnable assertions) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> assertions.run());
    }

    private void send(String key, String payload, String correlationId) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(EventTopics.FIX_ALLOCATION_INSTRUCTION, key, payload);
        record.headers().add(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
        producer.send(record);
    }

    private RawMessage awaitTerminalRawFor(String payload) {
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(rawMessages.findAll())
                        .anyMatch(r -> r.getPayload().equals(payload) && r.getStatus().isTerminal()));
        return rawMessages.findAll().stream()
                .filter(r -> r.getPayload().equals(payload) && r.getStatus().isTerminal())
                .findFirst().orElseThrow();
    }

    private void awaitOutboxFor(String aggregateId, String eventType) {
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(outboxEvents.findAll())
                        .anyMatch(o -> aggregateId.equals(o.getAggregateId())
                                && eventType.equals(o.getEventType())
                                && o.isPublished()));
    }

    @Test
    void validAllocationFlowsConsumeToPersistToPublishedEvent() {
        String allocId = "ALLOC-E2E-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);
        String correlationId = "corr-e2e-" + allocId;

        send(allocId, wire, correlationId);

        // --- raw persistence: verbatim payload + Kafka coordinates + correlation ---
        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.PROCESSED);
        assertThat(raw.getPayload()).isEqualTo(wire);
        assertThat(raw.getTopic()).isEqualTo(EventTopics.FIX_ALLOCATION_INSTRUCTION);
        assertThat(raw.getKafkaPartition()).isBetween(0, 2);
        assertThat(raw.getKafkaOffset()).isGreaterThanOrEqualTo(0);
        assertThat(raw.getMessageKey()).isEqualTo(allocId);
        assertThat(raw.getCorrelationId()).isEqualTo(correlationId);
        assertThat(raw.getReceivedAt()).isNotNull();
        assertThat(raw.getProcessedAt()).isNotNull();
        assertThat(raw.getFailureCategory()).isNull();

        // --- allocation aggregate with every repeating group ---
        inTransaction(() -> {
            Allocation allocation = allocations
                    .findByAllocIdAndAllocTransType(allocId, AllocTransType.NEW).orElseThrow();
            assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.RECEIVED);
            assertThat(allocation.getRawMessageId()).isEqualTo(raw.getId());
            assertThat(allocation.getQuantity()).isEqualByComparingTo("100");
            assertThat(allocation.getOrders()).hasSize(2);
            assertThat(allocation.getExecutions()).hasSize(2);
            assertThat(allocation.getAccounts()).hasSize(2);
            assertThat(allocation.getAccounts().get(0).getMiscFees()).hasSize(2);
        });

        // --- outbox published by the poller ---
        awaitOutboxFor(allocId, "AllocationReceived");

        // --- the Avro event on the wire: exactly one, complete envelope, exact decimals ---
        List<ConsumerRecord<String, Object>> events =
                KafkaTestConsumers.drainAvro(EventTopics.ALLOCATION_RECEIVED);
        List<AllocationReceived> mine = events.stream()
                .map(ConsumerRecord::value)
                .filter(AllocationReceived.class::isInstance)
                .map(AllocationReceived.class::cast)
                .filter(e -> e.getAllocId().equals(allocId))
                .toList();
        assertThat(mine).hasSize(1);
        AllocationReceived event = mine.get(0);
        assertThat(event.getMetadata().getCorrelationId()).isEqualTo(correlationId);
        assertThat(event.getMetadata().getCausationId()).isEqualTo(String.valueOf(raw.getId()));
        assertThat(event.getMetadata().getAggregateId()).isEqualTo(allocId);
        assertThat(event.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(event.getTradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(event.getRawMessageId()).isEqualTo(raw.getId());

        // Kafka record key + mirrored headers.
        ConsumerRecord<String, Object> wireRecord = events.stream()
                .filter(r -> r.value() instanceof AllocationReceived a && a.getAllocId().equals(allocId))
                .findFirst().orElseThrow();
        assertThat(wireRecord.key()).isEqualTo(allocId);
        assertThat(wireRecord.headers().lastHeader(EventHeaders.EVENT_ID)).isNotNull();
        assertThat(new String(wireRecord.headers().lastHeader(EventHeaders.CORRELATION_ID).value(),
                StandardCharsets.UTF_8)).isEqualTo(correlationId);
    }

    @Test
    void fieldInvalidMessageStoresAllErrorsAndPublishesRejection() {
        String allocId = "ALLOC-INV-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.multiErrorAllocation(allocId);

        send(allocId, wire, "corr-inv-" + allocId);

        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.INVALID);
        assertThat(raw.getFailureCategory()).isEqualTo(FailureCategory.FIELD_VALIDATION);
        assertThat(raw.getFailureSummary()).contains("validation error");

        // Scenario 7: ALL errors stored, with dictionary-resolved field detail.
        List<ValidationError> stored = validationErrors.findByRawMessageIdOrderByIdAsc(raw.getId());
        assertThat(stored.size()).isGreaterThanOrEqualTo(5);
        assertThat(stored).extracting(ValidationError::getErrorCode).contains(
                ValidationErrorCode.MISSING_REQUIRED_FIELD,
                ValidationErrorCode.VALUE_NOT_SUPPORTED,
                ValidationErrorCode.INVALID_VALUE_FORMAT);
        assertThat(stored).allMatch(e -> Objects.equals(e.getRawMessageId(), raw.getId()));

        // AllocTransType '9' is unsupported, so the message has no business identity:
        // no allocation row is created and the errors link to the raw message only.
        assertThat(allocations.findAll().stream()
                .filter(a -> a.getAllocId().equals(allocId))).isEmpty();
        assertThat(stored).allMatch(e -> e.getAllocationId() == null);

        // Rejection + exception events published; never an AllocationReceived.
        awaitOutboxFor(String.valueOf(raw.getId()), "AllocationRejected");
        List<AllocationRejected> rejections =
                KafkaTestConsumers.drainAvro(EventTopics.ALLOCATION_REJECTED).stream()
                        .map(ConsumerRecord::value)
                        .filter(AllocationRejected.class::isInstance)
                        .map(AllocationRejected.class::cast)
                        .filter(e -> e.getRawMessageId() == raw.getId())
                        .toList();
        assertThat(rejections).hasSize(1);
        assertThat(rejections.get(0).getAllocId()).isNull();
        assertThat(rejections.get(0).getErrors().size()).isGreaterThanOrEqualTo(5);

        assertThat(KafkaTestConsumers.drainAvro(EventTopics.ALLOCATION_RECEIVED).stream()
                .map(ConsumerRecord::value)
                .filter(AllocationReceived.class::isInstance)
                .map(AllocationReceived.class::cast)
                .filter(e -> e.getAllocId().equals(allocId)))
                .isEmpty();
    }

    @Test
    void logicallyInvalidMessageWithKnownIdentityPersistsAnInvalidAllocation() {
        String allocId = "ALLOC-SUM-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.sumMismatchAllocation(allocId);

        send(allocId, wire, "corr-sum-" + allocId);

        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.INVALID);

        // Identity parsed → the INVALID allocation row exists and errors link to it.
        Allocation allocation = allocations
                .findByAllocIdAndAllocTransType(allocId, AllocTransType.NEW).orElseThrow();
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.INVALID);
        List<ValidationError> stored = validationErrors.findByRawMessageIdOrderByIdAsc(raw.getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getErrorCode()).isEqualTo(ValidationErrorCode.CONFLICTING_FIELDS);
        assertThat(stored.get(0).getAllocationId()).isEqualTo(allocation.getId());

        // The rejection event carries the business identity.
        awaitOutboxFor(allocId, "AllocationRejected");
        List<AllocationRejected> rejections =
                KafkaTestConsumers.drainAvro(EventTopics.ALLOCATION_REJECTED).stream()
                        .map(ConsumerRecord::value)
                        .filter(AllocationRejected.class::isInstance)
                        .map(AllocationRejected.class::cast)
                        .filter(e -> allocId.equals(e.getAllocId()))
                        .toList();
        assertThat(rejections).hasSize(1);
    }

    @Test
    void newAllocationWithoutSettlDateIsInvalidAtIntakeAndNeverReachesOrchestration() {
        String allocId = "ALLOC-NOSD-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.withoutSettlDate(allocId, '0');

        send(allocId, wire, "corr-nosd-" + allocId);

        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.INVALID);
        // INVALID is terminal: orchestration never claims it, so no execution is ever bound.
        Allocation allocation = allocations
                .findByAllocIdAndAllocTransType(allocId, AllocTransType.NEW).orElseThrow();
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.INVALID);
        List<ValidationError> stored = validationErrors.findByRawMessageIdOrderByIdAsc(raw.getId());
        assertThat(stored).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode())
                        .isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD));

        awaitOutboxFor(allocId, "AllocationRejected");
    }

    @Test
    void malformedPayloadTerminatesAsParseFailedWithExceptionEvent() {
        String wire = FixTestMessages.malformedFix();

        send("no-key", wire, "corr-malformed");

        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.PARSE_FAILED);
        assertThat(raw.getFailureCategory()).isEqualTo(FailureCategory.MALFORMED_MESSAGE);
        assertThat(raw.getPayload()).isEqualTo(wire); // original payload preserved
        assertThat(raw.getFailureSummary()).doesNotContain("at com.").doesNotContain("Exception:");

        List<ValidationError> stored = validationErrors.findByRawMessageIdOrderByIdAsc(raw.getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getErrorCode()).isEqualTo(ValidationErrorCode.MALFORMED_MESSAGE);

        // Scenario 8: malformed input produces an exception-report event.
        awaitOutboxFor(String.valueOf(raw.getId()), "ExceptionReported");
        List<ExceptionReported> reports =
                KafkaTestConsumers.drainAvro(EventTopics.EXCEPTION_REPORTED).stream()
                        .map(ConsumerRecord::value)
                        .filter(ExceptionReported.class::isInstance)
                        .map(ExceptionReported.class::cast)
                        .filter(e -> e.getSourceRef().equals(String.valueOf(raw.getId())))
                        .toList();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getSourceType()).isEqualTo("RAW_MESSAGE");
    }

    @Test
    void unsupportedMessageTypeIsTerminalNotRetried() {
        String wire = FixTestMessages.unsupportedExecutionReport();

        send("exec-report", wire, "corr-unsupported");

        RawMessage raw = awaitTerminalRawFor(wire);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.PARSE_FAILED);
        assertThat(raw.getFailureCategory()).isEqualTo(FailureCategory.UNSUPPORTED_MESSAGE_TYPE);
        assertThat(raw.getFailureSummary()).contains("35");
        assertThat(raw.getDeliveryCount()).isEqualTo(1); // no retries for permanent failures
    }

    @Test
    void businessDuplicateIsAcknowledgedWithoutSecondEventSet() {
        String allocId = "ALLOC-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);

        // Scenario 14: the same instruction delivered twice at different offsets.
        send(allocId, wire, "corr-dup-1");
        send(allocId, wire, "corr-dup-2");

        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            List<RawMessage> rows = rawMessages.findAll().stream()
                    .filter(r -> r.getPayload().equals(wire)).toList();
            assertThat(rows).hasSize(2);
            assertThat(rows).allMatch(r -> r.getStatus().isTerminal());
        });

        List<RawMessage> rows = rawMessages.findAll().stream()
                .filter(r -> r.getPayload().equals(wire)).toList();
        assertThat(rows).extracting(RawMessage::getStatus)
                .containsExactlyInAnyOrder(RawMessageStatus.PROCESSED, RawMessageStatus.DUPLICATE);

        // One allocation row, one AllocationReceived outbox row, one wire event.
        assertThat(allocations.findByAllocIdAndAllocTransType(allocId, AllocTransType.NEW)).isPresent();
        List<OutboxEvent> outbox = outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(allocId)).toList();
        assertThat(outbox).hasSize(1);

        awaitOutboxFor(allocId, "AllocationReceived");
        assertThat(KafkaTestConsumers.drainAvro(EventTopics.ALLOCATION_RECEIVED).stream()
                .map(ConsumerRecord::value)
                .filter(AllocationReceived.class::isInstance)
                .map(AllocationReceived.class::cast)
                .filter(e -> e.getAllocId().equals(allocId)))
                .hasSize(1);
    }

    @Test
    void cancelWithTheSameAllocIdIsItsOwnInstructionNotADuplicate() {
        // Regression guard (testing review): dedup is keyed on (allocId, transType) — a
        // Cancel referencing an accepted New must be ACCEPTED, not absorbed as a duplicate.
        String allocId = "ALLOC-NC-" + UUID.randomUUID().toString().substring(0, 8);
        RawMessage newRaw = rawMessageService.recordArrival(
                "manual-topic", 0, 22000L, allocId, FixTestMessages.validNewAllocation(allocId), "corr-nc-1");
        assertThat(ingestionService.process(newRaw.getId())).isEqualTo(IngestionOutcome.ACCEPTED);

        RawMessage cancelRaw = rawMessageService.recordArrival(
                "manual-topic", 0, 22001L, allocId, FixTestMessages.validCancel(allocId, allocId), "corr-nc-2");
        assertThat(ingestionService.process(cancelRaw.getId())).isEqualTo(IngestionOutcome.ACCEPTED);

        assertThat(allocations.findByAllocIdAndAllocTransType(allocId, AllocTransType.NEW)).isPresent();
        assertThat(allocations.findByAllocIdAndAllocTransType(allocId, AllocTransType.CANCEL)).isPresent();

        // And a REAL duplicate of the Cancel is still absorbed.
        RawMessage dupCancel = rawMessageService.recordArrival(
                "manual-topic", 0, 22002L, allocId, FixTestMessages.validCancel(allocId, allocId), "corr-nc-3");
        assertThat(ingestionService.process(dupCancel.getId()))
                .isEqualTo(IngestionOutcome.DUPLICATE_BUSINESS);
    }

    @Test
    void retryExhaustionBookkeepingCreatesTheRowWhenArrivalNeverPersisted() {
        // Regression guard (testing review): if the original failure prevented even the
        // arrival insert, the DLT handler's bookkeeping must create the FAILED row itself
        // so the payload stays replayable from the database, with exactly one exception
        // event — and a second DLT delivery must not report twice.
        String allocId = "ALLOC-EXH-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);

        ingestionService.recordRetryExhaustion(
                "manual-topic", 0, 23000L, allocId, wire, "corr-exh", "simulated outage");

        RawMessage raw = rawMessages
                .findByTopicAndKafkaPartitionAndKafkaOffset("manual-topic", 0, 23000L).orElseThrow();
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.FAILED);
        assertThat(raw.getFailureCategory()).isEqualTo(FailureCategory.RETRY_EXHAUSTED);
        assertThat(raw.getPayload()).isEqualTo(wire);
        long exceptionEvents = outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(String.valueOf(raw.getId()))
                        && o.getEventType().equals("ExceptionReported"))
                .count();
        assertThat(exceptionEvents).isEqualTo(1);

        // Idempotent on DLT redelivery: terminal row → no second event.
        ingestionService.recordRetryExhaustion(
                "manual-topic", 0, 23000L, allocId, wire, "corr-exh", "simulated outage");
        long afterReplay = outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(String.valueOf(raw.getId()))
                        && o.getEventType().equals("ExceptionReported"))
                .count();
        assertThat(afterReplay).isEqualTo(1);
    }

    @Test
    void brokerRedeliveryOfTheSameCoordinatesIsProcessedOnce() {
        String allocId = "ALLOC-REDELIVER-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);

        // Exercise the exact service path the listener runs, with pinned coordinates.
        RawMessage first = rawMessageService.recordArrival(
                "manual-topic", 0, 12345L, allocId, wire, "corr-redeliver");
        assertThat(ingestionService.process(first.getId())).isEqualTo(IngestionOutcome.ACCEPTED);

        // Redelivery: same coordinates → same row, already terminal → nothing reprocessed.
        RawMessage second = rawMessageService.recordArrival(
                "manual-topic", 0, 12345L, allocId, wire, "corr-redeliver");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(RawMessageStatus.PROCESSED);
        assertThat(second.getStatus().isTerminal()).isTrue();
        assertThat(ingestionService.process(first.getId())).isEqualTo(IngestionOutcome.ALREADY_PROCESSED);

        // No duplicate domain record, no duplicate outbound event.
        assertThat(allocations.findAll().stream()
                .filter(a -> a.getAllocId().equals(allocId))).hasSize(1);
        assertThat(outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(allocId))).hasSize(1);
    }
}
