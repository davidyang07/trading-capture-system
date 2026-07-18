package com.tradingcapture.events;

import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Representative BACKWARD-compatibility checks for the evolution rule of
 * {@code .claude/rules/events.md}: evolution = additive optional fields with defaults;
 * a new required field breaks BACKWARD and demands a new v-major topic. BACKWARD means the
 * NEW schema (reader) must read data written with the CURRENT schema (writer) — the same
 * direction the compose Schema Registry enforces per subject.
 */
class SchemaCompatibilityTest {

    static Stream<Schema> allEventSchemas() {
        return Stream.of(
                AllocationReceived.getClassSchema(),
                AllocationValidated.getClassSchema(),
                AllocationRejected.getClassSchema(),
                ExecutionsAllocated.getClassSchema(),
                ExecutionsReleased.getClassSchema(),
                ExceptionReported.getClassSchema(),
                SettlementRequested.getClassSchema());
    }

    @ParameterizedTest
    @MethodSource("allEventSchemas")
    void everyEventEmbedsEventMetadataAsFirstField(Schema schema) {
        Schema.Field first = schema.getFields().get(0);
        assertThat(first.name()).isEqualTo("metadata");
        assertThat(first.schema().getFullName()).isEqualTo("com.tradingcapture.events.EventMetadata");
    }

    @ParameterizedTest
    @MethodSource("allEventSchemas")
    void addingAnOptionalFieldWithDefaultStaysBackwardCompatible(Schema current) {
        Schema evolved = withExtraField(current,
                new Schema.Field("newOptionalField",
                        Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING)),
                        "additive optional evolution", JsonProperties.NULL_VALUE));

        SchemaCompatibility.SchemaPairCompatibility result =
                SchemaCompatibility.checkReaderWriterCompatibility(evolved, current);

        assertThat(result.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @ParameterizedTest
    @MethodSource("allEventSchemas")
    void addingARequiredFieldWithoutDefaultBreaksBackwardCompatibility(Schema current) {
        Schema evolved = withExtraField(current,
                new Schema.Field("newRequiredField", Schema.create(Schema.Type.STRING),
                        "illegal in-place evolution", (Object) null));

        SchemaCompatibility.SchemaPairCompatibility result =
                SchemaCompatibility.checkReaderWriterCompatibility(evolved, current);

        assertThat(result.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    void evolvedReaderFillsTheDefaultWhenReadingCurrentData() throws Exception {
        AllocationReceived event = AllocationReceived.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId("e-1")
                        .setEventType("AllocationReceived")
                        .setSchemaVersion(1)
                        .setOccurredAt(Instant.parse("2026-07-16T12:00:00Z"))
                        .setCorrelationId("corr")
                        .setAggregateId("ALLOC-1")
                        .build())
                .setAllocId("ALLOC-1")
                .setAllocTransType("0")
                .setSide("1")
                .setQuantity(new BigDecimal("100.0000"))
                .setTradeDate(LocalDate.of(2026, 7, 15))
                .setRawMessageId(1L)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new SpecificDatumWriter<>(AllocationReceived.class).write(event, encoder);
        encoder.flush();

        Schema evolved = withExtraField(AllocationReceived.getClassSchema(),
                new Schema.Field("newOptionalField",
                        Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING)),
                        "additive optional evolution", JsonProperties.NULL_VALUE));

        GenericDatumReader<GenericRecord> reader =
                new GenericDatumReader<>(AllocationReceived.getClassSchema(), evolved);
        GenericRecord read = reader.read(null,
                DecoderFactory.get().binaryDecoder(out.toByteArray(), null));

        assertThat(read.get("newOptionalField")).isNull();
        assertThat(read.get("allocId")).hasToString("ALLOC-1");
    }

    @Test
    void eventMetadataDeclaresTheRequiredEnvelopeContract() {
        Schema schema = EventMetadata.getClassSchema();

        assertThat(schema.getFields()).extracting(Schema.Field::name).containsExactly(
                "eventId", "eventType", "schemaVersion", "occurredAt",
                "correlationId", "causationId", "aggregateId");
        assertThat(schema.getField("occurredAt").schema().getLogicalType().getName())
                .isEqualTo("timestamp-millis");

        Schema causation = schema.getField("causationId").schema();
        assertThat(causation.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(causation.getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(schema.getField("causationId").hasDefaultValue()).isTrue();
        assertThat(schema.getField("causationId").defaultVal()).isEqualTo(JsonProperties.NULL_VALUE);
    }

    @ParameterizedTest
    @MethodSource("allEventSchemas")
    void decimalFieldsUseTheMandatedPrecisionAndScale(Schema schema) {
        collectDecimalSchemas(schema, new ArrayList<>()).forEach(decimal -> {
            assertThat(decimal.getObjectProp("precision")).isEqualTo(19);
            assertThat(decimal.getObjectProp("scale")).isIn(4, 8);
        });
    }

    private static List<Schema> collectDecimalSchemas(Schema schema, List<Schema> found) {
        switch (schema.getType()) {
            case RECORD -> schema.getFields().forEach(f -> collectDecimalSchemas(f.schema(), found));
            case UNION -> schema.getTypes().forEach(s -> collectDecimalSchemas(s, found));
            case ARRAY -> collectDecimalSchemas(schema.getElementType(), found);
            case BYTES -> {
                if (schema.getLogicalType() != null && "decimal".equals(schema.getLogicalType().getName())) {
                    found.add(schema);
                }
            }
            default -> {
            }
        }
        return found;
    }

    /** Copies a record schema and appends one field — the "evolved" next version. */
    private static Schema withExtraField(Schema current, Schema.Field extra) {
        List<Schema.Field> fields = new ArrayList<>();
        current.getFields().forEach(f -> fields.add(new Schema.Field(f, f.schema())));
        fields.add(extra);
        // Same full name: subject compatibility compares versions of the SAME record.
        return Schema.createRecord(current.getName(), current.getDoc(),
                current.getNamespace(), false, fields);
    }
}
