package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.FailureCategory;
import com.tradingcapture.allocation.domain.model.RawMessage;
import com.tradingcapture.allocation.domain.model.ValidationError;
import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import com.tradingcapture.allocation.domain.model.ValidationErrorField;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.domain.repository.RawMessageRepository;
import com.tradingcapture.allocation.domain.repository.ValidationErrorRepository;
import com.tradingcapture.allocation.events.IngestionEventFactory;
import com.tradingcapture.allocation.fix.ExtractedAllocation;
import com.tradingcapture.allocation.fix.FixMessageParser;
import com.tradingcapture.allocation.fix.FixParseResult;
import com.tradingcapture.allocation.outbox.OutboxRecorder;
import com.tradingcapture.allocation.validation.AllocationInstructionValidator;
import com.tradingcapture.allocation.validation.CollectedError;
import com.tradingcapture.events.AllocationReceived;
import com.tradingcapture.events.AllocationRejected;
import com.tradingcapture.events.ExceptionReported;
import com.tradingcapture.events.EventTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The consume → parse → validate → persist → outbox pipeline for one raw message
 * (docs/architecture/event-flows.md). Runs AFTER the raw message is durably recorded
 * ({@link RawMessageService}) and inside ONE transaction, so the terminal raw-message
 * status, the allocation row, the stored validation errors, and the outbox events commit
 * atomically — a crash anywhere repeats the whole step idempotently on redelivery.
 *
 * <p>Aggregate type recorded on outbox rows for support queries.</p>
 */
@Service
public class AllocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AllocationIngestionService.class);

    private static final String AGGREGATE_ALLOCATION = "ALLOCATION";
    private static final String AGGREGATE_RAW_MESSAGE = "RAW_MESSAGE";

    private final RawMessageRepository rawMessages;
    private final AllocationRepository allocations;
    private final ValidationErrorRepository validationErrors;
    private final FixMessageParser parser;
    private final AllocationInstructionValidator validator;
    private final ParsedAllocationMapper mapper;
    private final IngestionEventFactory events;
    private final OutboxRecorder outbox;
    private final Clock clock;

    public AllocationIngestionService(RawMessageRepository rawMessages,
                                      AllocationRepository allocations,
                                      ValidationErrorRepository validationErrors,
                                      FixMessageParser parser,
                                      AllocationInstructionValidator validator,
                                      ParsedAllocationMapper mapper,
                                      IngestionEventFactory events,
                                      OutboxRecorder outbox,
                                      Clock clock) {
        this.rawMessages = rawMessages;
        this.allocations = allocations;
        this.validationErrors = validationErrors;
        this.parser = parser;
        this.validator = validator;
        this.mapper = mapper;
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public IngestionOutcome process(Long rawMessageId) {
        RawMessage raw = rawMessages.findById(rawMessageId).orElseThrow(
                () -> new IllegalStateException("raw_message " + rawMessageId + " vanished"));
        if (raw.getStatus().isTerminal()) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }

        return switch (parser.parse(raw.getPayload())) {
            case FixParseResult.Malformed malformed ->
                    parseFailure(raw, FailureCategory.MALFORMED_MESSAGE,
                            ValidationErrorCode.MALFORMED_MESSAGE, malformed.summary(),
                            IngestionOutcome.PARSE_FAILED);
            case FixParseResult.UnsupportedType unsupported ->
                    parseFailure(raw, FailureCategory.UNSUPPORTED_MESSAGE_TYPE,
                            ValidationErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                            "Unsupported MsgType (35): " + unsupported.msgType()
                                    + " — only AllocationInstruction (J) is ingested",
                            IngestionOutcome.UNSUPPORTED_TYPE);
            case FixParseResult.Parsed parsed -> processParsed(raw, parsed.extracted());
        };
    }

    private IngestionOutcome processParsed(RawMessage raw, ExtractedAllocation extracted) {
        Instant now = clock.instant();
        List<CollectedError> errors = validator.validate(extracted);
        AllocTransType transType = AllocTransType.fromCode(extracted.allocTransType());
        boolean identityKnown = extracted.allocId() != null && transType != null;

        // Business-duplicate absorption (ADR-0007): a resent New/Replace/Cancel is
        // acknowledged without reprocessing and without emitting a second event set.
        if (identityKnown
                && allocations.findByAllocIdAndAllocTransType(extracted.allocId(), transType).isPresent()) {
            raw.markDuplicate(now);
            log.info("business duplicate acknowledged [allocId={} transType={} rawMessageId={}]",
                    extracted.allocId(), transType, raw.getId());
            return IngestionOutcome.DUPLICATE_BUSINESS;
        }

        if (!errors.isEmpty()) {
            return invalid(raw, extracted, transType, identityKnown, errors, now);
        }

        if (!identityKnown) {
            // Unreachable: the validator demands AllocID and a supported AllocTransType.
            throw new IllegalStateException(
                    "Validator passed a message without a business identity (rawMessageId=" + raw.getId() + ")");
        }

        Allocation allocation = mapper.toAllocation(
                extracted, transType, AllocationStatus.RECEIVED, raw.getId(), now);
        saveGuardingDuplicateRace(allocation, raw);
        raw.markProcessed(now);

        AllocationReceived event = events.allocationReceived(allocation, raw.getCorrelationId());
        outbox.record(event.getMetadata(), event, EventTopics.ALLOCATION_RECEIVED,
                allocation.getAllocId(), AGGREGATE_ALLOCATION);

        log.info("allocation accepted [allocId={} transType={} rawMessageId={} allocationId={}]",
                allocation.getAllocId(), transType, raw.getId(), allocation.getId());
        return IngestionOutcome.ACCEPTED;
    }

    private IngestionOutcome invalid(RawMessage raw, ExtractedAllocation extracted,
                                     AllocTransType transType, boolean identityKnown,
                                     List<CollectedError> errors, Instant now) {
        Long allocationId = null;
        String allocId = null;
        if (identityKnown) {
            Allocation allocation = mapper.toAllocation(
                    extracted, transType, AllocationStatus.INVALID, raw.getId(), now);
            saveGuardingDuplicateRace(allocation, raw);
            allocationId = allocation.getId();
            allocId = allocation.getAllocId();
        }

        storeErrors(raw.getId(), allocationId, errors, now);
        String summary = errors.size() + " field validation error(s); first: " + errors.get(0).message();
        raw.markInvalid(truncate(summary), now);

        AllocationRejected rejected = events.allocationRejected(
                raw.getId(), allocId, errors, raw.getCorrelationId());
        outbox.record(rejected.getMetadata(), rejected, EventTopics.ALLOCATION_REJECTED,
                allocId != null ? allocId : String.valueOf(raw.getId()),
                allocId != null ? AGGREGATE_ALLOCATION : AGGREGATE_RAW_MESSAGE);

        String sourceType = allocId != null
                ? IngestionEventFactory.SOURCE_ALLOCATION : IngestionEventFactory.SOURCE_RAW_MESSAGE;
        String sourceRef = allocId != null ? allocId : String.valueOf(raw.getId());
        ExceptionReported exception = events.exceptionReported(sourceType, sourceRef,
                truncate(summary), errors, raw.getCorrelationId(), raw.getId());
        outbox.record(exception.getMetadata(), exception, EventTopics.EXCEPTION_REPORTED,
                sourceRef, allocId != null ? AGGREGATE_ALLOCATION : AGGREGATE_RAW_MESSAGE);

        log.info("allocation rejected by field validation [allocId={} rawMessageId={} errors={}]",
                allocId, raw.getId(), errors.size());
        return IngestionOutcome.INVALID;
    }

    private IngestionOutcome parseFailure(RawMessage raw, FailureCategory category,
                                          ValidationErrorCode code, String summary,
                                          IngestionOutcome outcome) {
        Instant now = clock.instant();
        raw.markParseFailed(category, truncate(summary), now);

        ValidationError error = new ValidationError(raw.getId(), null, code, truncate(summary), now);
        validationErrors.save(error);

        ExceptionReported event = events.exceptionReported(
                IngestionEventFactory.SOURCE_RAW_MESSAGE, String.valueOf(raw.getId()),
                truncate(summary), List.of(new CollectedError(code, truncate(summary), List.of())),
                raw.getCorrelationId(), raw.getId());
        outbox.record(event.getMetadata(), event, EventTopics.EXCEPTION_REPORTED,
                String.valueOf(raw.getId()), AGGREGATE_RAW_MESSAGE);

        log.warn("inbound message not processable [rawMessageId={} category={}]: {}",
                raw.getId(), category, truncate(summary));
        return outcome;
    }

    /**
     * Dead-letter bookkeeping (called from the DLT handler, own transaction): the message
     * exhausted its transient retries. Marks the raw message FAILED — creating it first if
     * the original failure prevented even the arrival insert — and reports the exception.
     */
    @Transactional
    public void recordRetryExhaustion(String topic, int partition, long offset, String messageKey,
                                      String payload, String correlationId, String safeSummary) {
        Instant now = clock.instant();
        RawMessage raw = rawMessages.findByTopicAndKafkaPartitionAndKafkaOffset(topic, partition, offset)
                .orElseGet(() -> rawMessages.save(new RawMessage(
                        topic, partition, offset, messageKey, payload, correlationId, now)));
        if (raw.getStatus().isTerminal()) {
            return; // outcome already durable — nothing to report twice
        }
        raw.markFailed(truncate(safeSummary), now);

        ExceptionReported event = events.exceptionReported(
                IngestionEventFactory.SOURCE_RAW_MESSAGE, String.valueOf(raw.getId()),
                truncate("Retries exhausted: " + safeSummary), List.of(),
                raw.getCorrelationId(), raw.getId());
        outbox.record(event.getMetadata(), event, EventTopics.EXCEPTION_REPORTED,
                String.valueOf(raw.getId()), AGGREGATE_RAW_MESSAGE);
    }

    private void storeErrors(Long rawMessageId, Long allocationId,
                             List<CollectedError> errors, Instant now) {
        for (CollectedError collected : errors) {
            ValidationError entity = new ValidationError(
                    rawMessageId, allocationId, collected.code(), truncate(collected.message()), now);
            for (CollectedError.FieldRef field : collected.fields()) {
                entity.addField(new ValidationErrorField(
                        field.fieldId(), field.fieldName(), truncateField(field.value()), now));
            }
            validationErrors.save(entity);
        }
    }

    /**
     * A lost duplicate-insert race on uq_allocations_business_key surfaces as transient:
     * the bounded redelivery re-runs the duplicate check and terminates as DUPLICATE.
     */
    private void saveGuardingDuplicateRace(Allocation allocation, RawMessage raw) {
        try {
            allocations.saveAndFlush(allocation);
        } catch (DataIntegrityViolationException e) {
            throw new TransientIngestionException(
                    "Concurrent allocation insert for allocId=" + allocation.getAllocId()
                            + " (rawMessageId=" + raw.getId() + ")", e);
        }
    }

    private static String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static String truncateField(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
