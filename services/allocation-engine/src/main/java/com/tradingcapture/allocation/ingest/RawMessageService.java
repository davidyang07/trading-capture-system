package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.domain.model.RawMessage;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Durable arrival record for every consumed Kafka record — committed in its OWN transaction
 * (REQUIRES_NEW) before any parsing, so the verbatim payload survives even a crash during
 * processing. The unique (topic, partition, offset) key makes broker redelivery a lookup
 * instead of a second insert (ADR-0007).
 */
@Service
public class RawMessageService {

    private final RawMessageRepository rawMessages;
    private final Clock clock;

    public RawMessageService(RawMessageRepository rawMessages, Clock clock) {
        this.rawMessages = rawMessages;
        this.clock = clock;
    }

    /**
     * Inserts the arrival record, or returns the existing row for a redelivery. On a
     * concurrent-insert race the unique key wins and the existing row is returned.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RawMessage recordArrival(String topic, int partition, long offset,
                                    String messageKey, String payload, String correlationId) {
        return rawMessages.findByTopicAndKafkaPartitionAndKafkaOffset(topic, partition, offset)
                .map(existing -> {
                    if (!existing.getStatus().isTerminal()) {
                        existing.recordRedelivery(clock.instant());
                    }
                    return existing;
                })
                .orElseGet(() -> insertNew(topic, partition, offset, messageKey, payload, correlationId));
    }

    private RawMessage insertNew(String topic, int partition, long offset,
                                 String messageKey, String payload, String correlationId) {
        try {
            return rawMessages.saveAndFlush(new RawMessage(
                    topic, partition, offset, messageKey, payload, correlationId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Lost a concurrent-insert race on uq_raw_messages_coordinates: the row now
            // exists. Surface as transient so the bounded redelivery resolves it via the
            // lookup path (this transaction is already rollback-only).
            throw new TransientIngestionException(
                    "Concurrent raw_message insert for " + topic + "-" + partition + "@" + offset, e);
        }
    }
}
