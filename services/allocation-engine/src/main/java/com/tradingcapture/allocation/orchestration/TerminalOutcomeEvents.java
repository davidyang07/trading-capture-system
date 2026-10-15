package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import com.tradingcapture.allocation.events.OrchestrationEventFactory;
import com.tradingcapture.allocation.outbox.OutboxRecorder;
import com.tradingcapture.allocation.validation.logical.LogicalValidationError;
import com.tradingcapture.events.EventTopics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Announces an allocation's unsuccessful outcome. Call it inside the transaction that commits the
 * outcome, so the events and the state change commit together.
 *
 * <ul>
 *   <li>REJECTED publishes AllocationRejected with every reason, plus an ExceptionReported.</li>
 *   <li>FAILED publishes an ExceptionReported (no field-level errors, per its contract).</li>
 * </ul>
 */
@Component
public class TerminalOutcomeEvents {

    private static final String AGGREGATE = "ALLOCATION";

    private final OutboxRecorder outbox;
    private final OrchestrationEventFactory events;
    private final RawMessageRepository rawMessages;

    public TerminalOutcomeEvents(OutboxRecorder outbox, OrchestrationEventFactory events,
                                 RawMessageRepository rawMessages) {
        this.outbox = outbox;
        this.events = events;
        this.rawMessages = rawMessages;
    }

    public void rejected(Allocation a, List<LogicalValidationError> errors) {
        String correlationId = correlationId(a);
        var rejected = events.allocationRejected(a, errors, correlationId);
        outbox.record(rejected.getMetadata(), rejected, EventTopics.ALLOCATION_REJECTED, a.getAllocId(), AGGREGATE);
        var exception = events.exceptionReported(a, "REJECTED: " + a.getFailureSummary(), errors, correlationId);
        outbox.record(exception.getMetadata(), exception, EventTopics.EXCEPTION_REPORTED, a.getAllocId(), AGGREGATE);
    }

    public void failed(Allocation a) {
        var exception = events.exceptionReported(a,
                "FAILED (" + a.getFailureCategory() + "): " + a.getFailureSummary(), List.of(), correlationId(a));
        outbox.record(exception.getMetadata(), exception, EventTopics.EXCEPTION_REPORTED, a.getAllocId(), AGGREGATE);
    }

    /** The inbound message's correlation id, so the outcome joins the same trace as the instruction. */
    String correlationId(Allocation a) {
        return rawMessages.findById(a.getRawMessageId())
                .map(raw -> raw.getCorrelationId())
                .filter(id -> id != null && !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
