package com.tradingcapture.allocation.testsupport;

import com.tradingcapture.testsupport.KafkaTestContainer;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared wiring for full-context integration tests: singleton MySQL (Flyway applies the
 * real migration) + singleton Kafka (real broker, retry-topic behavior representative) +
 * the in-memory {@code mock://} schema registry shared between the application's Avro
 * producer and test consumers. Concrete classes add {@code @SpringBootTest} and their own
 * topic/group overrides where they need consumer isolation from other cached contexts.
 */
public abstract class AllocationIntegrationTestSupport extends MySqlContainerSupport {

    /** One mock-registry scope for the whole test JVM (producer and consumers must share it). */
    public static final String MOCK_REGISTRY = "mock://allocation-engine-it";

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::bootstrapServers);
        registry.add("tcs.kafka.schema-registry-url", () -> MOCK_REGISTRY);
        // Fast outbox ticks keep awaitility waits short without changing semantics.
        registry.add("tcs.outbox.poll-interval", () -> "100ms");
        // The scheduled orchestration driver stays off in ingestion tests (no downstream services);
        // orchestration tests drive it explicitly. Redis is not required when it is disabled.
        registry.add("tcs.orchestration.enabled", () -> "false");
    }
}
