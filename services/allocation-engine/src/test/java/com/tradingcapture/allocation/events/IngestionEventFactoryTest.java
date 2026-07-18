package com.tradingcapture.allocation.events;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import com.tradingcapture.allocation.validation.CollectedError;
import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.AllocationRejected;
import com.tradingcapture.events.ExceptionReported;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionEventFactoryTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-16T12:00:00Z");

    private final IngestionEventFactory factory =
            new IngestionEventFactory(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    private static Allocation received() {
        Allocation allocation = new Allocation(
                "ALLOC-1", AllocTransType.NEW, AllocationStatus.RECEIVED, 42L, FIXED_NOW);
        allocation.setInstructionFields("1", "1", null, "1",
                new BigDecimal("100.2500"), new BigDecimal("101.25000000"), "USD",
                LocalDate.of(2026, 7, 15), Instant.parse("2026-07-15T13:45:30Z"),
                "0", LocalDate.of(2026, 7, 17), null, null);
        return allocation;
    }

    @Test
    void allocationReceivedCarriesTheFullEnvelopeAndExactDecimals() {
        AllocationReceived event = factory.allocationReceived(received(), "corr-abc");

        assertThat(event.getMetadata().getEventId()).isNotBlank();
        assertThat(event.getMetadata().getEventType()).isEqualTo("AllocationReceived");
        assertThat(event.getMetadata().getSchemaVersion()).isEqualTo(1);
        assertThat(event.getMetadata().getOccurredAt()).isEqualTo(FIXED_NOW);
        assertThat(event.getMetadata().getCorrelationId()).isEqualTo("corr-abc");
        assertThat(event.getMetadata().getCausationId()).isEqualTo("42"); // the raw message
        assertThat(event.getMetadata().getAggregateId()).isEqualTo("ALLOC-1");
        assertThat(event.getQuantity()).isEqualByComparingTo("100.25");
        assertThat(event.getQuantity().scale()).isEqualTo(4); // Avro decimal contract
        assertThat(event.getTradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(event.getRawMessageId()).isEqualTo(42L);
    }

    @Test
    void twoEventsNeverShareAnEventId() {
        assertThat(factory.allocationReceived(received(), "corr").getMetadata().getEventId())
                .isNotEqualTo(factory.allocationReceived(received(), "corr").getMetadata().getEventId());
    }

    @Test
    void rejectionWithoutABusinessIdentityKeysOnTheRawMessage() {
        List<CollectedError> errors = List.of(CollectedError.of(
                ValidationErrorCode.MISSING_REQUIRED_FIELD, "AllocID (70) is required",
                new CollectedError.FieldRef(70, "AllocID", null)));

        AllocationRejected event = factory.allocationRejected(7L, null, errors, "corr-def");

        assertThat(event.getAllocId()).isNull();
        assertThat(event.getMetadata().getAggregateId()).isEqualTo("7");
        assertThat(event.getErrors()).hasSize(1);
        assertThat(event.getErrors().get(0).getErrorCode()).isEqualTo("MISSING_REQUIRED_FIELD");
        assertThat(event.getErrors().get(0).getFields().get(0).getFieldName()).isEqualTo("AllocID");
        assertThat(event.getErrors().get(0).getFields().get(0).getFieldValue()).isNull();
    }

    @Test
    void exceptionReportedMirrorsTheSourceReference() {
        ExceptionReported event = factory.exceptionReported(
                IngestionEventFactory.SOURCE_RAW_MESSAGE, "9", "FIX syntax invalid",
                List.of(), "corr-ghi", 9L);

        assertThat(event.getSourceType()).isEqualTo("RAW_MESSAGE");
        assertThat(event.getSourceRef()).isEqualTo("9");
        assertThat(event.getMetadata().getAggregateId()).isEqualTo("9");
        assertThat(event.getMetadata().getCausationId()).isEqualTo("9");
        assertThat(event.getErrors()).isEmpty();
    }
}
