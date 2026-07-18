package com.tradingcapture.events;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the generated contracts round-trip through the actual Confluent serializer stack
 * (wire framing + registry registration under TopicNameStrategy) — the alignment the
 * known-gaps register asked Phase 6 to verify. Uses the serializer's in-memory
 * {@code mock://} registry; the compose Schema Registry enforces the same BACKWARD rule.
 */
class EventSerializationRoundTripTest {

    private static final String SCOPE = "event-contracts-round-trip";

    private static KafkaAvroSerializer serializer;
    private static KafkaAvroDeserializer deserializer;

    @BeforeAll
    static void configureSerde() {
        Map<String, Object> config = Map.of(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://" + SCOPE,
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        serializer = new KafkaAvroSerializer();
        serializer.configure(config, false);
        deserializer = new KafkaAvroDeserializer();
        deserializer.configure(config, false);
    }

    @AfterAll
    static void dropMockRegistryScope() {
        serializer.close();
        deserializer.close();
        MockSchemaRegistry.dropScope(SCOPE);
    }

    private static EventMetadata metadata(String eventType, String aggregateId) {
        return EventMetadata.newBuilder()
                .setEventId("11111111-2222-3333-4444-555555555555")
                .setEventType(eventType)
                .setSchemaVersion(1)
                .setOccurredAt(Instant.parse("2026-07-16T12:00:00Z"))
                .setCorrelationId("corr-abc")
                .setCausationId("42")
                .setAggregateId(aggregateId)
                .build();
    }

    @Test
    void allocationReceivedRoundTripsWithDecimalAndDateFidelity() {
        AllocationReceived event = AllocationReceived.newBuilder()
                .setMetadata(metadata("AllocationReceived", "ALLOC-1"))
                .setAllocId("ALLOC-1")
                .setAllocTransType("0")
                .setSide("1")
                .setQuantity(new BigDecimal("1500.2500"))
                .setTradeDate(LocalDate.of(2026, 7, 15))
                .setRawMessageId(42L)
                .build();

        byte[] wire = serializer.serialize(EventTopics.ALLOCATION_RECEIVED, event);
        Object read = deserializer.deserialize(EventTopics.ALLOCATION_RECEIVED, wire);

        assertThat(read).isInstanceOf(AllocationReceived.class);
        AllocationReceived roundTripped = (AllocationReceived) read;
        assertThat(roundTripped.getQuantity()).isEqualByComparingTo(new BigDecimal("1500.2500"));
        assertThat(roundTripped.getQuantity().scale()).isEqualTo(4);
        assertThat(roundTripped.getTradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(roundTripped.getMetadata().getOccurredAt()).isEqualTo(Instant.parse("2026-07-16T12:00:00Z"));
        assertThat(roundTripped).isEqualTo(event);
    }

    @Test
    void topicNameStrategyRegistersOneSubjectPerTopic() throws Exception {
        serializer.serialize(EventTopics.ALLOCATION_RECEIVED, AllocationReceived.newBuilder()
                .setMetadata(metadata("AllocationReceived", "ALLOC-2"))
                .setAllocId("ALLOC-2")
                .setAllocTransType("0")
                .setSide("2")
                .setQuantity(new BigDecimal("10.0000"))
                .setTradeDate(LocalDate.of(2026, 7, 15))
                .setRawMessageId(43L)
                .build());

        SchemaRegistryClient mockRegistry = MockSchemaRegistry.getClientForScope(SCOPE);
        assertThat(mockRegistry.getAllSubjects())
                .contains(EventTopics.ALLOCATION_RECEIVED + "-value");
    }

    @Test
    void nullableFieldsCarryExplicitNullDefaults() {
        AllocationRejected event = AllocationRejected.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId("aaaa-bbbb")
                        .setEventType("AllocationRejected")
                        .setSchemaVersion(1)
                        .setOccurredAt(Instant.parse("2026-07-16T12:00:00Z"))
                        .setCorrelationId("corr-def")
                        .setCausationId(null)
                        .setAggregateId("7")
                        .build())
                .setAllocId(null)
                .setRawMessageId(7L)
                .setErrors(List.of(ValidationErrorDetail.newBuilder()
                        .setErrorCode("MISSING_REQUIRED_FIELD")
                        .setMessage("AllocID (70) is required")
                        .setFields(List.of(ValidationErrorField.newBuilder()
                                .setFieldId(70)
                                .setFieldName("AllocID")
                                .setFieldValue(null)
                                .build()))
                        .build()))
                .build();

        byte[] wire = serializer.serialize(EventTopics.ALLOCATION_REJECTED, event);
        AllocationRejected read = (AllocationRejected) deserializer.deserialize(EventTopics.ALLOCATION_REJECTED, wire);

        assertThat(read.getAllocId()).isNull();
        assertThat(read.getMetadata().getCausationId()).isNull();
        assertThat(read.getErrors()).hasSize(1);
        assertThat(read.getErrors().get(0).getFields().get(0).getFieldValue()).isNull();
    }

    @Test
    void exceptionReportedErrorListDefaultsToEmpty() {
        ExceptionReported event = ExceptionReported.newBuilder()
                .setMetadata(metadata("ExceptionReported", "9"))
                .setSourceType("RAW_MESSAGE")
                .setSourceRef("9")
                .setSummary("retries exhausted")
                .build();

        byte[] wire = serializer.serialize(EventTopics.EXCEPTION_REPORTED, event);
        ExceptionReported read = (ExceptionReported) deserializer.deserialize(EventTopics.EXCEPTION_REPORTED, wire);

        assertThat(read.getErrors()).isEmpty();
        assertThat(read.getSummary()).isEqualTo("retries exhausted");
    }
}
