package com.tradingcapture.allocation.outbox;

import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.AllocationRejected;
import com.tradingcapture.events.AllocationValidated;
import com.tradingcapture.events.ExceptionReported;
import com.tradingcapture.events.ExecutionsAllocated;
import com.tradingcapture.events.ExecutionsReleased;
import com.tradingcapture.events.SettlementRequested;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Encodes outbox payloads as plain Avro binary (writer schema = the generated class's
 * classpath schema) and re-hydrates them for publication through the Confluent serializer.
 * Round-tripping within one deployment is schema-stable because the poller and the recorder
 * share the same generated classes. Registered types are exactly the events Phase 7 emits;
 * Phase 8 extends the registry with its orchestration events.
 */
@Component
public class AvroPayloadCodec {

    private static final Map<String, Class<? extends SpecificRecord>> EVENT_TYPES = Map.of(
            AllocationReceived.class.getSimpleName(), AllocationReceived.class,
            AllocationRejected.class.getSimpleName(), AllocationRejected.class,
            ExceptionReported.class.getSimpleName(), ExceptionReported.class,
            AllocationValidated.class.getSimpleName(), AllocationValidated.class,
            ExecutionsAllocated.class.getSimpleName(), ExecutionsAllocated.class,
            ExecutionsReleased.class.getSimpleName(), ExecutionsReleased.class,
            SettlementRequested.class.getSimpleName(), SettlementRequested.class);

    public byte[] encode(SpecificRecord event) {
        requireRegistered(event.getClass().getSimpleName());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter<SpecificRecord>(event.getSchema()).write(event, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Avro encoding failed for " + event.getClass().getSimpleName(), e);
        }
    }

    public SpecificRecord decode(String eventType, byte[] payload) {
        Class<? extends SpecificRecord> type = requireRegistered(eventType);
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
            return new SpecificDatumReader<>(type).read(null, decoder);
        } catch (IOException e) {
            throw new UncheckedIOException("Avro decoding failed for " + eventType, e);
        }
    }

    private static Class<? extends SpecificRecord> requireRegistered(String eventType) {
        Class<? extends SpecificRecord> type = EVENT_TYPES.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        }
        return type;
    }
}
