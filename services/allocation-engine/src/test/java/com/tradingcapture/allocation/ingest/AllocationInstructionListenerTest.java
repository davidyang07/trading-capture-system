package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.observability.IngestionMetrics;
import com.tradingcapture.events.EventHeaders;
import com.tradingcapture.events.EventTopics;
import com.tradingcapture.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AllocationInstructionListenerTest {

    private final RawMessageService rawMessageService = mock(RawMessageService.class);
    private final AllocationIngestionService ingestionService = mock(AllocationIngestionService.class);
    private final AllocationInstructionListener listener = new AllocationInstructionListener(
            rawMessageService, ingestionService, new IngestionMetrics(new SimpleMeterRegistry()));

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(EventTopics.FIX_ALLOCATION_INSTRUCTION, 0, 5L, "key", value);
    }

    @Test
    void undeserializableRecordIsAcknowledgedWithoutTouchingThePipeline() {
        // ErrorHandlingDeserializer surfaces a failed deserialization as a null value:
        // nothing durable can be done, so the record must be acked — never retried, never
        // allowed to NPE into the transient-retry path.
        listener.onMessage(record(null));

        verifyNoInteractions(rawMessageService, ingestionService);
    }

    @Test
    void wellFormedCorrelationHeaderIsPreserved() {
        ConsumerRecord<String, String> record = record("payload");
        record.headers().add(EventHeaders.CORRELATION_ID, "corr-Abc_1.2".getBytes(StandardCharsets.UTF_8));

        assertThat(AllocationInstructionListener.correlationId(record)).isEqualTo("corr-Abc_1.2");
    }

    @Test
    void unsafeCorrelationHeadersAreReplacedNotPropagated() {
        // Log-forging control characters.
        ConsumerRecord<String, String> forged = record("payload");
        forged.headers().add(EventHeaders.CORRELATION_ID,
                "evil\r\nFAKE LOG LINE".getBytes(StandardCharsets.UTF_8));
        String sanitized = AllocationInstructionListener.correlationId(forged);
        assertThat(sanitized).doesNotContain("\r").doesNotContain("\n").matches("[0-9a-f-]{36}");

        // Values that would overflow the VARCHAR(64) audit column.
        ConsumerRecord<String, String> oversized = record("payload");
        oversized.headers().add(EventHeaders.CORRELATION_ID,
                "x".repeat(65).getBytes(StandardCharsets.UTF_8));
        assertThat(AllocationInstructionListener.correlationId(oversized)).hasSize(36);

        // Absent header → generated.
        assertThat(AllocationInstructionListener.correlationId(record("payload"))).hasSize(36);
    }

    @Test
    void kafkaHeaderConstantsStayInSyncAcrossLibraries() {
        // EventHeaders (event-contracts) and CorrelationId (observability-support) define
        // the same wire header independently; this pins them together.
        assertThat(EventHeaders.CORRELATION_ID).isEqualTo(CorrelationId.KAFKA_HEADER);
    }

}
