package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.ValidationErrorCodeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One collected validation error for an inbound message (ADR-0012: ALL errors are stored).
 * Always linked to the raw message; additionally linked to the allocation when the business
 * identity parsed. Immutable audit rows.
 */
@Entity
@Table(name = "validation_errors")
public class ValidationError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "raw_message_id", nullable = false, updatable = false)
    private Long rawMessageId;

    @Column(name = "allocation_id", updatable = false)
    private Long allocationId;

    @Convert(converter = ValidationErrorCodeConverter.class)
    @Column(name = "error_code", length = 40, nullable = false, updatable = false)
    private ValidationErrorCode errorCode;

    @Column(name = "message", length = 500, nullable = false, updatable = false)
    private String message;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "validation_error_id", nullable = false)
    private List<ValidationErrorField> fields = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ValidationError() {
        // JPA
    }

    public ValidationError(Long rawMessageId, Long allocationId, ValidationErrorCode errorCode,
                           String message, Instant now) {
        this.rawMessageId = rawMessageId;
        this.allocationId = allocationId;
        this.errorCode = errorCode;
        this.message = message;
        this.createdAt = now;
    }

    public void addField(ValidationErrorField field) {
        fields.add(field);
    }

    public Long getId() {
        return id;
    }

    public Long getRawMessageId() {
        return rawMessageId;
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public ValidationErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public List<ValidationErrorField> getFields() {
        return fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
