package com.tradingcapture.allocation.domain.model;

/**
 * Typed validation-error taxonomy (ADR-0012). Codes are stable strings shared
 * verbatim with the {@code ValidationErrorDetail} event contract.
 */
public enum ValidationErrorCode implements CodeEnum {

    /** FIX syntax could not be parsed at all (message-level, no field breakdown). */
    MALFORMED_MESSAGE,
    /** MsgType (35) is not a supported type (message-level). */
    UNSUPPORTED_MESSAGE_TYPE,
    /** A dictionary-required field (or group-required field) is absent. */
    MISSING_REQUIRED_FIELD,
    /** A field value does not parse as its FIX type (number, date, timestamp). */
    INVALID_VALUE_FORMAT,
    /** A parseable value outside the supported domain (e.g. AllocTransType '7'). */
    VALUE_NOT_SUPPORTED,
    /** A parseable value outside legal bounds (e.g. Quantity <= 0). */
    VALUE_OUT_OF_BOUND,
    /** Two or more fields are individually valid but mutually inconsistent. */
    CONFLICTING_FIELDS;

    @Override
    public String code() {
        return name();
    }
}
