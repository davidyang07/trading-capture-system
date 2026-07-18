package com.tradingcapture.allocation.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/**
 * Producer wiring (consumers are Boot-auto-configured from {@code spring.kafka.consumer}).
 * Durability defaults: {@code acks=all}, idempotent producer, snappy compression. Two templates:
 * <ul>
 *   <li>{@code stringKafkaTemplate} — raw-string records; also the template
 *       {@code @RetryableTopic} uses to forward records to retry/DLT topics;</li>
 *   <li>{@code avroKafkaTemplate} — outbox domain events through the Confluent serializer
 *       (TopicNameStrategy subjects, BACKWARD-compat registry; ADR-0004).</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final String schemaRegistryUrl;

    public KafkaConfig(KafkaProperties kafkaProperties,
                       @Value("${tcs.kafka.schema-registry-url}") String schemaRegistryUrl) {
        this.kafkaProperties = kafkaProperties;
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> config = baseProducerConfig();
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    @Bean
    public ProducerFactory<String, SpecificRecord> avroProducerFactory() {
        Map<String, Object> config = baseProducerConfig();
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // TopicNameStrategy is the serializer default (one event type per topic, ADR-0004);
        // first publication of each event type registers its subject.
        config.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, SpecificRecord> avroKafkaTemplate() {
        return new KafkaTemplate<>(avroProducerFactory());
    }

    private Map<String, Object> baseProducerConfig() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return config;
    }
}
