package com.tradingcapture.testsupport;

import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Kafka (KRaft) Testcontainer shared by every integration test in a JVM. Like the
 * MySQL/Redis singletons it starts once and is never explicitly stopped (Ryuk reaps it at
 * JVM exit). The image matches the docker-compose broker so retry-topic and producer
 * behavior are representative.
 *
 * <p>Not a base class (tests typically also extend the MySQL base): point
 * {@code spring.kafka.bootstrap-servers} at {@link #bootstrapServers()} from a
 * {@code @DynamicPropertySource}. Schema-registry-dependent serializers should use the
 * in-memory {@code mock://} registry in tests.</p>
 */
public final class KafkaTestContainer {

    private static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"));

    static {
        KAFKA.start();
    }

    private KafkaTestContainer() {
    }

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
