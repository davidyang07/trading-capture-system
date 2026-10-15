package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Transactional-outbox row (ADR-0005): written in the same transaction as the state change
 * it describes, published by the scheduled poller in id order, then stamped published_at.
 * The payload is the Avro binary encoding of the generated event class named by
 * {@code eventType}; the poller re-hydrates and publishes through the Confluent serializer.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", length = 60, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "aggregate_type", length = 40, nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 64, nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "topic", length = 255, nullable = false, updatable = false)
    private String topic;

    @Column(name = "message_key", length = 64, nullable = false, updatable = false)
    private String messageKey;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] payload;

    @Column(name = "correlation_id", length = 64, nullable = false, updatable = false)
    private String correlationId;

    @Column(name = "causation_id", length = 64, updatable = false)
    private String causationId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Set when the row can never be published; the poller skips it from then on (V4). */
    @Column(name = "parked_at")
    private Instant parkedAt;

    protected OutboxEvent() {
        // JPA
    }

    public OutboxEvent(String eventId, String eventType, String aggregateType, String aggregateId,
                       String topic, String messageKey, byte[] payload, String correlationId,
                       String causationId, Instant now) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.attempts = 0;
        this.createdAt = now;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
        this.lastError = null;
    }

    public void recordFailedAttempt(String safeError) {
        this.attempts++;
        this.lastError = safeError;
    }

    /** This row can never be published (see {@code OutboxPoller}): stop retrying it. */
    public void park(String safeError, Instant now) {
        this.attempts++;
        this.lastError = safeError;
        this.parkedAt = now;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean isParked() {
        return parkedAt != null;
    }

    public Instant getParkedAt() {
        return parkedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
