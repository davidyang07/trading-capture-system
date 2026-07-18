package com.tradingcapture.allocation.domain.model;

/** Why an inbound message failed (safe classification only — never a payload dump). */
public enum FailureCategory implements CodeEnum {

    /** FIX syntax could not be parsed (garbled tags, bad checksum/body, invalid field types). */
    MALFORMED_MESSAGE,
    /** Parsed, but the MsgType (35) is not AllocationInstruction (J). */
    UNSUPPORTED_MESSAGE_TYPE,
    /** Parsed, but the collect-all field validator found errors (ADR-0012). */
    FIELD_VALIDATION,
    /** Transient-failure retries exhausted; the message landed on the DLT. */
    RETRY_EXHAUSTED;

    @Override
    public String code() {
        return name();
    }
}
