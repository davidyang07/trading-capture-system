package com.tradingcapture.allocation;

import com.tradingcapture.allocation.domain.model.FailureCategory;
import com.tradingcapture.allocation.domain.model.RawMessage;
import com.tradingcapture.allocation.domain.model.RawMessageStatus;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import com.tradingcapture.allocation.fix.FixTestMessages;
import com.tradingcapture.allocation.ingest.AllocationIngestionService;
import com.tradingcapture.allocation.ingest.TransientIngestionException;
import com.tradingcapture.allocation.testsupport.AllocationIntegrationTestSupport;
import com.tradingcapture.allocation.testsupport.KafkaTestConsumers;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * Real-broker retry topology: transient failures redeliver through the {@code -retry-0/1/2}
 * topics with bounded attempts and exhaust onto the {@code -dlt}. Uses its own inbound
 * topic + consumer group so cached contexts of other test classes cannot steal records, a
 * fast deterministic backoff (no production-length waits), and a disabled outbox poller
 * (the pipeline test's context is the single publisher against the shared database).
 */
@SpringBootTest(properties = {
        "tcs.ingest.inbound-topic=" + IngestionRetryDltIntegrationTest.TOPIC,
        "spring.kafka.consumer.group-id=allocation-engine-retry-it",
        // Distinct intervals (100/200/400 ms) keep the production topology: spring-kafka
        // collapses EQUAL intervals onto a single reused "-retry" topic, while different
        // intervals give the indexed -retry-0/1/2 topics the catalog documents.
        "tcs.ingest.retry.initial-delay-ms=100",
        "tcs.ingest.retry.multiplier=2.0",
        "tcs.outbox.enabled=false"
})
class IngestionRetryDltIntegrationTest extends AllocationIntegrationTestSupport {

    static final String TOPIC = "tcs.fix.allocation-instruction.retry-it.v1";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private RawMessageRepository rawMessages;
    @Autowired
    private AllocationRepository allocations;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    @Qualifier("stringKafkaTemplate")
    private KafkaTemplate<String, String> producer;

    @MockitoSpyBean
    private AllocationIngestionService ingestionService;

    @AfterEach
    void resetSpy() {
        Mockito.reset(ingestionService);
    }

    private RawMessage rawFor(String payload) {
        return rawMessages.findAll().stream()
                .filter(r -> r.getPayload().equals(payload))
                .findFirst().orElseThrow();
    }

    @Test
    void transientFailureRetriesBoundedlyAndResumesWithoutDuplicates() {
        String allocId = "ALLOC-RETRY-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);

        // First two attempts fail transiently, the third runs the real pipeline.
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) {
                throw new TransientIngestionException("simulated transient failure", null);
            }
            return invocation.callRealMethod();
        }).when(ingestionService).process(anyLong());

        producer.send(TOPIC, allocId, wire);

        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(rawMessages.findAll())
                        .anyMatch(r -> r.getPayload().equals(wire)
                                && r.getStatus() == RawMessageStatus.PROCESSED));

        RawMessage raw = rawFor(wire);
        // Every retry hop mapped onto the SAME raw row via the original-coordinate headers,
        // preserving the attempt trail: main + retry-0 + retry-1 = 3 deliveries.
        assertThat(raw.getDeliveryCount()).isEqualTo(3);
        assertThat(raw.getTopic()).isEqualTo(TOPIC);

        // A later successful retry creates no duplicate domain records or events.
        assertThat(allocations.findAll().stream()
                .filter(a -> a.getAllocId().equals(allocId))).hasSize(1);
        assertThat(outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(allocId))).hasSize(1);

        // The retry topics really carried the record (non-blocking retry, real broker).
        assertThat(KafkaTestConsumers.drainStrings(TOPIC + "-retry-0"))
                .anyMatch(r -> wire.equals(r.value()));
        assertThat(KafkaTestConsumers.drainStrings(TOPIC + "-retry-1"))
                .anyMatch(r -> wire.equals(r.value()));
    }

    @Test
    void exhaustedRetriesLandOnTheDltWithDiagnosticsAndTerminalState() {
        String allocId = "ALLOC-DLT-" + UUID.randomUUID().toString().substring(0, 8);
        String wire = FixTestMessages.validNewAllocation(allocId);

        doAnswer(invocation -> {
            throw new TransientIngestionException("simulated permanent outage", null);
        }).when(ingestionService).process(anyLong());

        producer.send(TOPIC, allocId, wire);

        // DLT bookkeeping runs the REAL recordRetryExhaustion (only process() is stubbed).
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(rawMessages.findAll())
                        .anyMatch(r -> r.getPayload().equals(wire)
                                && r.getStatus() == RawMessageStatus.FAILED));

        RawMessage raw = rawFor(wire);
        assertThat(raw.getFailureCategory()).isEqualTo(FailureCategory.RETRY_EXHAUSTED);
        assertThat(raw.getFailureSummary()).contains("exhausted")
                .doesNotContain("at com.").doesNotContain("Authorization");
        // 4 bounded attempts: main + retry-0/1/2 — attempt trail preserved, then stop.
        assertThat(raw.getDeliveryCount()).isEqualTo(4);
        assertThat(raw.getPayload()).isEqualTo(wire); // recoverable for replay

        // ExceptionReported recorded through the outbox (poller off in this context).
        assertThat(outboxEvents.findAll())
                .anyMatch(o -> o.getEventType().equals("ExceptionReported")
                        && o.getAggregateId().equals(String.valueOf(raw.getId())));

        // The DLT record itself retains payload and original coordinates.
        List<ConsumerRecord<String, String>> dlt = KafkaTestConsumers.drainStrings(TOPIC + "-dlt");
        List<ConsumerRecord<String, String>> mine = dlt.stream()
                .filter(r -> wire.equals(r.value())).toList();
        assertThat(mine).hasSize(1); // exactly one DLT record — no DLT recursion
        assertThat(mine.get(0).headers().lastHeader("kafka_original-topic")).isNotNull();

        // No allocation and no acceptance event were ever produced.
        assertThat(allocations.findAll().stream()
                .filter(a -> a.getAllocId().equals(allocId))).isEmpty();
        assertThat(outboxEvents.findAll().stream()
                .filter(o -> o.getAggregateId().equals(allocId))).isEmpty();
    }
}
