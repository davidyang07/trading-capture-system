package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.FailureCategoryConverter;
import com.tradingcapture.allocation.domain.model.converter.RawMessageStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One inbound Kafka record, persisted verbatim before any destructive parsing (ADR-0007).
 * The payload and Kafka coordinates never change after insert; only the processing-state
 * columns (status, failure fields, processed_at, delivery_count) transition — from
 * {@link RawMessageStatus#RECEIVED} to exactly one terminal state.
 */
@Entity
@Table(name = "raw_messages")
public class RawMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "topic", length = 255, nullable = false, updatable = false)
    private String topic;

    @Column(name = "kafka_partition", nullable = false, updatable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false, updatable = false)
    private long kafkaOffset;

    @Column(name = "message_key", length = 255, updatable = false)
    private String messageKey;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "correlation_id", length = 64, nullable = false, updatable = false)
    private String correlationId;

    @Convert(converter = RawMessageStatusConverter.class)
    @Column(name = "status", length = 20, nullable = false)
    private RawMessageStatus status;

    @Convert(converter = FailureCategoryConverter.class)
    @Column(name = "failure_category", length = 40)
    private FailureCategory failureCategory;

    @Column(name = "failure_summary", length = 500)
    private String failureSummary;

    @Column(name = "delivery_count", nullable = false)
    private int deliveryCount;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RawMessage() {
        // JPA
    }

    public RawMessage(String topic, int kafkaPartition, long kafkaOffset, String messageKey,
                      String payload, String correlationId, Instant receivedAt) {
        this.topic = topic;
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
        this.messageKey = messageKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.status = RawMessageStatus.RECEIVED;
        this.deliveryCount = 1;
        this.receivedAt = receivedAt;
        this.createdAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    /** Records a redelivery observation on a still-unprocessed row. */
    public void recordRedelivery(Instant now) {
        this.deliveryCount++;
        this.updatedAt = now;
    }

    public void markProcessed(Instant now) {
        transitionTo(RawMessageStatus.PROCESSED, null, null, now);
    }

    public void markInvalid(String summary, Instant now) {
        transitionTo(RawMessageStatus.INVALID, FailureCategory.FIELD_VALIDATION, summary, now);
    }

    public void markParseFailed(FailureCategory category, String summary, Instant now) {
        if (category != FailureCategory.MALFORMED_MESSAGE
                && category != FailureCategory.UNSUPPORTED_MESSAGE_TYPE) {
            throw new IllegalArgumentException("Not a parse-level failure category: " + category);
        }
        transitionTo(RawMessageStatus.PARSE_FAILED, category, summary, now);
    }

    public void markDuplicate(Instant now) {
        transitionTo(RawMessageStatus.DUPLICATE, null, null, now);
    }

    public void markFailed(String summary, Instant now) {
        transitionTo(RawMessageStatus.FAILED, FailureCategory.RETRY_EXHAUSTED, summary, now);
    }

    private void transitionTo(RawMessageStatus target, FailureCategory category,
                              String summary, Instant now) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "raw_message " + id + " already terminal (" + status + "), cannot become " + target);
        }
        this.status = target;
        this.failureCategory = category;
        this.failureSummary = summary;
        this.processedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public int getKafkaPartition() {
        return kafkaPartition;
    }

    public long getKafkaOffset() {
        return kafkaOffset;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public RawMessageStatus getStatus() {
        return status;
    }

    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
