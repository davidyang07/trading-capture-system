package com.tradingcapture.allocation.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deterministic processing-state machine of an inbound record. */
class RawMessageTest {

    private static final Instant T0 = Instant.parse("2026-07-16T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-16T12:00:01Z");

    private static RawMessage received() {
        return new RawMessage("tcs.fix.allocation-instruction.v1", 0, 5L, "ALLOC-1",
                "8=FIX.4.4...", "corr-1", T0);
    }

    @Test
    void arrivesInReceivedWithBookkeeping() {
        RawMessage raw = received();

        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.RECEIVED);
        assertThat(raw.getStatus().isTerminal()).isFalse();
        assertThat(raw.getDeliveryCount()).isEqualTo(1);
        assertThat(raw.getReceivedAt()).isEqualTo(T0);
        assertThat(raw.getProcessedAt()).isNull();
    }

    @Test
    void everyTerminalTransitionStampsProcessedAt() {
        RawMessage processed = received();
        processed.markProcessed(T1);
        assertThat(processed.getStatus()).isEqualTo(RawMessageStatus.PROCESSED);
        assertThat(processed.getProcessedAt()).isEqualTo(T1);
        assertThat(processed.getFailureCategory()).isNull();

        RawMessage invalid = received();
        invalid.markInvalid("3 field validation error(s)", T1);
        assertThat(invalid.getStatus()).isEqualTo(RawMessageStatus.INVALID);
        assertThat(invalid.getFailureCategory()).isEqualTo(FailureCategory.FIELD_VALIDATION);
        assertThat(invalid.getFailureSummary()).contains("3 field");

        RawMessage failed = received();
        failed.markFailed("retries exhausted", T1);
        assertThat(failed.getStatus()).isEqualTo(RawMessageStatus.FAILED);
        assertThat(failed.getFailureCategory()).isEqualTo(FailureCategory.RETRY_EXHAUSTED);
    }

    @Test
    void terminalRowsRefuseFurtherTransitions() {
        RawMessage raw = received();
        raw.markProcessed(T1);

        assertThatThrownBy(() -> raw.markInvalid("late", T1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
        assertThatThrownBy(() -> raw.markFailed("late", T1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.PROCESSED);
    }

    @Test
    void parseFailureAcceptsOnlyParseLevelCategories() {
        RawMessage raw = received();

        assertThatThrownBy(() -> raw.markParseFailed(FailureCategory.RETRY_EXHAUSTED, "x", T1))
                .isInstanceOf(IllegalArgumentException.class);

        raw.markParseFailed(FailureCategory.MALFORMED_MESSAGE, "FIX syntax invalid", T1);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.PARSE_FAILED);
    }

    @Test
    void redeliveryOnlyIncrementsTheCounter() {
        RawMessage raw = received();
        raw.recordRedelivery(T1);

        assertThat(raw.getDeliveryCount()).isEqualTo(2);
        assertThat(raw.getStatus()).isEqualTo(RawMessageStatus.RECEIVED);
    }

    @Test
    void allocationCreationIsRestrictedToIngestionStates() {
        assertThatThrownBy(() -> new Allocation("A", AllocTransType.NEW,
                AllocationStatus.COMPLETED, 1L, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
