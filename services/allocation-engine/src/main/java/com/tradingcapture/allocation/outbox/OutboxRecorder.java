package com.tradingcapture.allocation.outbox;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import com.tradingcapture.allocation.domain.repository.OutboxEventRepository;
import com.tradingcapture.events.EventMetadata;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

/**
 * Appends an outbox row for a domain event (ADR-0005). MUST be called inside the same
 * transaction as the state change the event describes — enforced with a hard check so a
 * dual-write can never slip in silently. Publication is the poller's job.
 */
@Component
public class OutboxRecorder {

    private final OutboxEventRepository outboxEvents;
    private final AvroPayloadCodec codec;
    private final Clock clock;

    public OutboxRecorder(OutboxEventRepository outboxEvents, AvroPayloadCodec codec, Clock clock) {
        this.outboxEvents = outboxEvents;
        this.codec = codec;
        this.clock = clock;
    }

    public void record(EventMetadata metadata, SpecificRecord event, String topic,
                       String messageKey, String aggregateType) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Outbox events must be recorded inside the state-changing transaction (ADR-0005)");
        }
        outboxEvents.save(new OutboxEvent(
                metadata.getEventId(),
                metadata.getEventType(),
                aggregateType,
                metadata.getAggregateId(),
                topic,
                messageKey,
                codec.encode(event),
                metadata.getCorrelationId(),
                metadata.getCausationId(),
                clock.instant()));
    }
}
