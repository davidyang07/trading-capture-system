package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A FIX field implicated in a validation error (dictionary-resolved name). Immutable. */
@Entity
@Table(name = "validation_error_fields")
public class ValidationErrorField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "field_id", updatable = false)
    private Integer fieldId;

    @Column(name = "field_name", length = 100, updatable = false)
    private String fieldName;

    @Column(name = "field_value", length = 255, updatable = false)
    private String fieldValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ValidationErrorField() {
        // JPA
    }

    public ValidationErrorField(Integer fieldId, String fieldName, String fieldValue, Instant now) {
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Integer getFieldId() {
        return fieldId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
