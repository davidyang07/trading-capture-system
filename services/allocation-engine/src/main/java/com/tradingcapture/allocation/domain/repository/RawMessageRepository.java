package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.RawMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RawMessageRepository extends JpaRepository<RawMessage, Long> {

    /** Broker-redelivery lookup on the unique Kafka coordinates (ADR-0007). */
    Optional<RawMessage> findByTopicAndKafkaPartitionAndKafkaOffset(String topic, int partition, long offset);
}
