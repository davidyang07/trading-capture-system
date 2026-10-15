package com.tradingcapture.allocation.events;

import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.validation.CollectedError;
import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.AllocationRejected;
import com.tradingcapture.events.EventMetadata;
import com.tradingcapture.events.ExceptionReported;
import com.tradingcapture.events.ValidationErrorDetail;
import com.tradingcapture.events.ValidationErrorField;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Builds the Phase 7 ingestion-outcome events (docs/contracts/event-catalog.md) with a
 * complete {@link EventMetadata} envelope. The causation of every first-level ingestion
 * event is the raw inbound message.
 */
@Component
public class IngestionEventFactory {

    /** Source-type discriminators for ExceptionReported.sourceRef (see the .avsc doc). */
    public static final String SOURCE_RAW_MESSAGE = "RAW_MESSAGE";
    public static final String SOURCE_ALLOCATION = "ALLOCATION";

    private static final int EVENT_SCHEMA_VERSION = 1;

    private final Clock clock;

    public IngestionEventFactory(Clock clock) {
        this.clock = clock;
    }

    public AllocationReceived allocationReceived(Allocation allocation, String correlationId) {
        return AllocationReceived.newBuilder()
                .setMetadata(metadata(AllocationReceived.class.getSimpleName(), correlationId,
                        String.valueOf(allocation.getRawMessageId()), allocation.getAllocId()))
                .setAllocId(allocation.getAllocId())
                .setAllocTransType(allocation.getAllocTransType().code())
                .setSide(allocation.getSide())
                .setQuantity(allocation.getQuantity().setScale(4, RoundingMode.UNNECESSARY))
                .setTradeDate(allocation.getTradeDate())
                .setRawMessageId(allocation.getRawMessageId())
                .build();
    }

    /**
     * @param allocId     the AllocID the sender used, when readable (null otherwise)
     * @param aggregateId the allocId when an allocation row was stored, else the raw-message id
     */
    public AllocationRejected allocationRejected(Long rawMessageId, String allocId, String aggregateId,
                                                 List<CollectedError> errors, String correlationId) {
        return AllocationRejected.newBuilder()
                .setMetadata(metadata(AllocationRejected.class.getSimpleName(), correlationId,
                        String.valueOf(rawMessageId), aggregateId))
                .setAllocId(allocId)
                .setRawMessageId(rawMessageId)
                .setErrors(toDetails(errors))
                .build();
    }

    public ExceptionReported exceptionReported(String sourceType, String sourceRef, String summary,
                                               List<CollectedError> errors, String correlationId,
                                               Long rawMessageId) {
        return ExceptionReported.newBuilder()
                .setMetadata(metadata(ExceptionReported.class.getSimpleName(), correlationId,
                        String.valueOf(rawMessageId), sourceRef))
                .setSourceType(sourceType)
                .setSourceRef(sourceRef)
                .setSummary(summary)
                .setErrors(toDetails(errors))
                .build();
    }

    private EventMetadata metadata(String eventType, String correlationId,
                                   String causationId, String aggregateId) {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setSchemaVersion(EVENT_SCHEMA_VERSION)
                .setOccurredAt(clock.instant())
                .setCorrelationId(correlationId)
                .setCausationId(causationId)
                .setAggregateId(aggregateId)
                .build();
    }

    private static List<ValidationErrorDetail> toDetails(List<CollectedError> errors) {
        // Same bounds as the validation_errors tables (500/255): the published payload must
        // never amplify a hostile oversized field value beyond what is persisted.
        return errors.stream()
                .map(error -> ValidationErrorDetail.newBuilder()
                        .setErrorCode(error.code().code())
                        .setMessage(truncate(error.message(), 500))
                        .setFields(error.fields().stream()
                                .map(field -> ValidationErrorField.newBuilder()
                                        .setFieldId(field.fieldId())
                                        .setFieldName(field.fieldName())
                                        .setFieldValue(truncate(field.value(), 255))
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
