package com.tradingcapture.allocation.testsupport;

import com.tradingcapture.testsupport.KafkaTestContainer;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded drains of a topic from the beginning with a throwaway consumer group. Callers
 * first await the durable state that guarantees the expected records were published, so
 * the drain is a read of already-present data, not a race.
 */
public final class KafkaTestConsumers {

    private static final Duration POLL = Duration.ofMillis(250);
    private static final int QUIET_POLLS_TO_STOP = 8;   // 2s of silence ends the drain
    private static final Duration HARD_DEADLINE = Duration.ofSeconds(20);

    private KafkaTestConsumers() {
    }

    /** All Avro records currently on the topic (mock-registry deserialization). */
    public static List<ConsumerRecord<String, Object>> drainAvro(String topic) {
        Map<String, Object> config = base();
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                AllocationIntegrationTestSupport.MOCK_REGISTRY);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return drain(topic, config);
    }

    /** All String records currently on the topic (inbound/retry/DLT payloads). */
    public static List<ConsumerRecord<String, String>> drainStrings(String topic) {
        Map<String, Object> config = base();
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return drain(topic, config);
    }

    private static <V> List<ConsumerRecord<String, V>> drain(String topic, Map<String, Object> config) {
        List<ConsumerRecord<String, V>> collected = new ArrayList<>();
        try (KafkaConsumer<String, V> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + HARD_DEADLINE.toNanos();
            int quietPolls = 0;
            while (quietPolls < QUIET_POLLS_TO_STOP && System.nanoTime() < deadline) {
                ConsumerRecords<String, V> records = consumer.poll(POLL);
                if (records.isEmpty()) {
                    quietPolls++;
                } else {
                    quietPolls = 0;
                    records.forEach(collected::add);
                }
            }
        }
        return collected;
    }

    private static Map<String, Object> base() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "drain-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return config;
    }
}
