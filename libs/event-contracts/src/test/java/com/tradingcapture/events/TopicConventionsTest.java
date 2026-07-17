package com.tradingcapture.events;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog ↔ code sync check (docs/contracts/event-catalog.md): topic naming convention,
 * one topic per event schema, and the header names that mirror the envelope.
 */
class TopicConventionsTest {

    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^tcs\\.[a-z][a-z-]*\\.[a-z][a-z-]*\\.v\\d+$");

    /** Every event record ↔ its topic; kept literal so a rename breaks loudly. */
    private static final Map<String, String> EVENT_TO_TOPIC = Map.of(
            AllocationReceived.class.getSimpleName(), EventTopics.ALLOCATION_RECEIVED,
            AllocationValidated.class.getSimpleName(), EventTopics.ALLOCATION_VALIDATED,
            AllocationRejected.class.getSimpleName(), EventTopics.ALLOCATION_REJECTED,
            ExecutionsAllocated.class.getSimpleName(), EventTopics.EXECUTIONS_ALLOCATED,
            ExecutionsReleased.class.getSimpleName(), EventTopics.EXECUTIONS_RELEASED,
            ExceptionReported.class.getSimpleName(), EventTopics.EXCEPTION_REPORTED,
            SettlementRequested.class.getSimpleName(), EventTopics.SETTLEMENT_REQUESTED);

    @Test
    void everyTopicFollowsTheNamingConvention() {
        assertThat(EventTopics.FIX_ALLOCATION_INSTRUCTION).matches(TOPIC_PATTERN);
        EventTopics.OUTBOUND_TOPICS.forEach(topic -> assertThat(topic).matches(TOPIC_PATTERN));
    }

    @Test
    void everyOutboundEventHasExactlyOneTopicNamedAfterIt() {
        assertThat(EventTopics.OUTBOUND_TOPICS)
                .containsExactlyInAnyOrderElementsOf(EVENT_TO_TOPIC.values())
                .doesNotHaveDuplicates();

        EVENT_TO_TOPIC.forEach((eventName, topic) -> {
            String kebab = eventName
                    .replaceAll("([a-z])([A-Z])", "$1-$2")
                    .toLowerCase();
            assertThat(topic)
                    .as("topic for %s embeds its kebab-case event name", eventName)
                    .isEqualTo("tcs.allocation." + kebab + ".v1");
        });
    }

    @Test
    void everyEventSchemaDocumentsItsTopic() {
        EVENT_TO_TOPIC.forEach((eventName, topic) -> {
            Schema schema = schemaFor(eventName);
            assertThat(schema.getDoc())
                    .as("%s schema doc names its topic", eventName)
                    .contains(topic);
        });
    }

    @Test
    void headerNamesMirrorTheEnvelopeConventions() {
        // Must match CorrelationId.KAFKA_HEADER in libs/observability-support (kept
        // dependency-free here; the literal is the contract).
        assertThat(EventHeaders.CORRELATION_ID).isEqualTo("x-correlation-id");
        assertThat(EventHeaders.CAUSATION_ID).isEqualTo("x-causation-id");
        assertThat(EventHeaders.EVENT_ID).isEqualTo("x-event-id");
    }

    @Test
    void retryAndDltSuffixesFollowTheCatalog() {
        assertThat(EventTopics.RETRY_SUFFIX).isEqualTo("-retry");
        assertThat(EventTopics.DLT_SUFFIX).isEqualTo("-dlt");
    }

    private static Schema schemaFor(String simpleName) {
        try {
            Class<?> type = Class.forName("com.tradingcapture.events." + simpleName);
            return (Schema) type.getMethod("getClassSchema").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Generated event class missing: " + simpleName, e);
        }
    }
}
