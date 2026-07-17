package com.tradingcapture.allocation.outbox;

import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.EventMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroPayloadCodecTest {

    private final AvroPayloadCodec codec = new AvroPayloadCodec();

    @Test
    void roundTripsAnEventThroughBinaryEncoding() {
        AllocationReceived event = AllocationReceived.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId("e-1")
                        .setEventType("AllocationReceived")
                        .setSchemaVersion(1)
                        .setOccurredAt(Instant.parse("2026-07-16T12:00:00Z"))
                        .setCorrelationId("corr-1")
                        .setCausationId("42")
                        .setAggregateId("ALLOC-1")
                        .build())
                .setAllocId("ALLOC-1")
                .setAllocTransType("0")
                .setSide("1")
                .setQuantity(new BigDecimal("100.0000"))
                .setTradeDate(LocalDate.of(2026, 7, 15))
                .setRawMessageId(42L)
                .build();

        byte[] payload = codec.encode(event);
        Object decoded = codec.decode("AllocationReceived", payload);

        assertThat(decoded).isEqualTo(event);
    }

    @Test
    void unknownEventTypesFailLoudly() {
        assertThatThrownBy(() -> codec.decode("SettlementRequested", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SettlementRequested");
    }
}
