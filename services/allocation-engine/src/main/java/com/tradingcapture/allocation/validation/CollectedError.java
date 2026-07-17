package com.tradingcapture.allocation.validation;

import com.tradingcapture.allocation.domain.model.ValidationErrorCode;

import java.util.List;

/**
 * One typed validation error with the FIX fields it implicates (ADR-0012 taxonomy,
 * modernized from the reference {@code GenericFieldError}). Feeds both the
 * {@code validation_errors} tables and the {@code ValidationErrorDetail} event record.
 */
public record CollectedError(ValidationErrorCode code, String message, List<FieldRef> fields) {

    /** A FIX field reference with its dictionary-resolved name and offending value. */
    public record FieldRef(Integer fieldId, String fieldName, String value) {
    }

    public static CollectedError of(ValidationErrorCode code, String message, FieldRef... fields) {
        return new CollectedError(code, message, List.of(fields));
    }
}
