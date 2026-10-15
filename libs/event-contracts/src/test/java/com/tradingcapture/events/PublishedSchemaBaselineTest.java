package com.tradingcapture.events;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every event schema must stay compatible with the schema as published, kept as a baseline under
 * {@code src/test/resources/schema-baseline} (see the README there). Without a baseline, a breaking
 * change to a schema could only ever be compared with itself.
 *
 * <p>Compatibility is required both ways: consumers on the new schema read old events (BACKWARD),
 * and consumers still on the old schema read new events (FORWARD). Avro's compatibility check
 * ignores logical types, so a decimal whose scale changes from 4 to 8 would pass it while every
 * value is read off by 10⁴. The logical types are therefore compared field by field.</p>
 */
class PublishedSchemaBaselineTest {

    private static final String BASELINE = "/schema-baseline/";

    static Stream<Schema> currentEventSchemas() {
        return Stream.of(
                AllocationReceived.getClassSchema(),
                AllocationValidated.getClassSchema(),
                AllocationRejected.getClassSchema(),
                ExecutionsAllocated.getClassSchema(),
                ExecutionsReleased.getClassSchema(),
                ExceptionReported.getClassSchema(),
                SettlementRequested.getClassSchema());
    }

    /** Baseline schemas parsed with one parser, shared records first, as the build compiles them. */
    private static Map<String, Schema> baseline() throws IOException {
        Schema.Parser parser = new Schema.Parser();
        Map<String, Schema> schemas = new LinkedHashMap<>();
        for (String name : List.of("EventMetadata", "ValidationErrorDetail", "AllocationReceived",
                "AllocationValidated", "AllocationRejected", "ExecutionsAllocated", "ExecutionsReleased",
                "ExceptionReported", "SettlementRequested")) {
            try (InputStream in = PublishedSchemaBaselineTest.class.getResourceAsStream(BASELINE + name + ".avsc")) {
                assertThat(in).as("baseline for %s", name).isNotNull();
                Schema schema = parser.parse(in);
                schemas.put(schema.getFullName(), schema);
            }
        }
        return schemas;
    }

    @ParameterizedTest
    @MethodSource("currentEventSchemas")
    void everyEventHasAPublishedBaseline(Schema current) throws IOException {
        assertThat(baseline()).containsKey(current.getFullName());
    }

    @ParameterizedTest
    @MethodSource("currentEventSchemas")
    void newReadersReadPublishedEvents(Schema current) throws IOException {
        Schema published = baseline().get(current.getFullName());

        var result = SchemaCompatibility.checkReaderWriterCompatibility(current, published);

        assertThat(result.getType()).as(result.getDescription())
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @ParameterizedTest
    @MethodSource("currentEventSchemas")
    void publishedReadersReadNewEvents(Schema current) throws IOException {
        Schema published = baseline().get(current.getFullName());

        var result = SchemaCompatibility.checkReaderWriterCompatibility(published, current);

        assertThat(result.getType()).as(result.getDescription())
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @ParameterizedTest
    @MethodSource("currentEventSchemas")
    void logicalTypesAreUnchanged(Schema current) throws IOException {
        Schema published = baseline().get(current.getFullName());

        assertThat(logicalTypeChanges(published, current)).isEmpty();
    }

    @Test
    void aDecimalScaleChangeIsCaughtEvenThoughAvroCallsItCompatible() throws IOException {
        Schema published = baseline().get(SettlementRequested.getClassSchema().getFullName());
        Schema rescaled = new Schema.Parser().parse(published.toString()
                .replace("\"scale\":4", "\"scale\":8"));

        assertThat(SchemaCompatibility.checkReaderWriterCompatibility(rescaled, published).getType())
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
        assertThat(logicalTypeChanges(published, rescaled)).isNotEmpty()
                .allMatch(change -> change.contains("decimal(19,4)") && change.contains("decimal(19,8)"));
    }

    /** Each path whose logical type differs between the schemas, e.g. "accounts[].quantity". */
    private static List<String> logicalTypeChanges(Schema before, Schema after) {
        List<String> changes = new ArrayList<>();
        compare("", before, after, changes);
        return changes;
    }

    private static void compare(String path, Schema before, Schema after, List<String> changes) {
        String was = describe(before.getLogicalType());
        String now = describe(after.getLogicalType());
        if (!was.equals(now)) {
            changes.add(path + ": " + was + " -> " + now);
        }
        if (before.getType() != after.getType()) {
            return; // a type change is the compatibility checks' business
        }
        switch (before.getType()) {
            case RECORD -> before.getFields().forEach(field -> {
                Schema.Field match = after.getField(field.name());
                if (match != null) {
                    compare(path.isEmpty() ? field.name() : path + "." + field.name(),
                            field.schema(), match.schema(), changes);
                }
            });
            case ARRAY -> compare(path + "[]", before.getElementType(), after.getElementType(), changes);
            case UNION -> {
                for (Schema branch : before.getTypes()) {
                    after.getTypes().stream()
                            .filter(candidate -> candidate.getType() == branch.getType())
                            .findFirst()
                            .ifPresent(candidate -> compare(path, branch, candidate, changes));
                }
            }
            default -> {
            }
        }
    }

    private static String describe(LogicalType type) {
        if (type == null) {
            return "none";
        }
        if (type instanceof LogicalTypes.Decimal decimal) {
            return "decimal(" + decimal.getPrecision() + "," + decimal.getScale() + ")";
        }
        return type.getName();
    }
}
