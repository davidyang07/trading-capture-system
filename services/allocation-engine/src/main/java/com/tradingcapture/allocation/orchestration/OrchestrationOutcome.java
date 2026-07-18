package com.tradingcapture.allocation.orchestration;

/** Result of advancing one orchestration step; {@code ADVANCED} means the driver should continue. */
public enum OrchestrationOutcome {
    ADVANCED,
    COMPLETED,
    REJECTED,
    RETRY_SCHEDULED,
    FAILED,
    SKIPPED
}
