package com.tradingcapture.allocation.domain.model;

/**
 * Lifecycle of one allocation-to-execution assignment. An assignment is {@code ASSIGNED} when
 * created and {@code RELEASED} exactly once (Replace/Cancel); released rows are retained for
 * audit, never deleted.
 */
public enum AssignmentStatus implements CodeEnum {

    ASSIGNED,
    RELEASED;

    @Override
    public String code() {
        return name();
    }
}
