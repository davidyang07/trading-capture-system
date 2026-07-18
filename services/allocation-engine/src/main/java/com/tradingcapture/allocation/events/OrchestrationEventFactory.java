package com.tradingcapture.allocation.events;

import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationAccount;
import com.tradingcapture.allocation.domain.model.AllocationAssignment;
import com.tradingcapture.allocation.domain.model.AllocationOrder;
import com.tradingcapture.events.AllocatedExecution;
import com.tradingcapture.events.AllocationValidated;
import com.tradingcapture.events.EventMetadata;
import com.tradingcapture.events.ExecutionsAllocated;
import com.tradingcapture.events.ExecutionsReleased;
import com.tradingcapture.events.SettlementAccount;
import com.tradingcapture.events.SettlementRequested;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Builds the Phase 8 orchestration outcome events (AllocationValidated, ExecutionsAllocated,
 * ExecutionsReleased, SettlementRequested) with a complete {@link EventMetadata} envelope. Causation
 * is the inbound raw message; the aggregate id and Kafka key are the business allocId, giving
 * deterministic per-allocation ordering. Decimal quantities are emitted at scale 4 (ADR-0014).
 *
 * <p>Execution identifiers in these events are the internal execution ids stored on the assignments
 * (the ExecID→internal mapping is resolved once at orchestration entry), so the identifier is stable
 * and identical across a New's ExecutionsAllocated and a later Replace/Cancel's ExecutionsReleased.</p>
 */
@Component
public class OrchestrationEventFactory {

    private static final int EVENT_SCHEMA_VERSION = 1;

    private final Clock clock;

    public OrchestrationEventFactory(Clock clock) {
        this.clock = clock;
    }

    public AllocationValidated allocationValidated(Allocation allocation, String correlationId) {
        List<String> orderIds = allocation.getOrders().stream()
                .map(OrchestrationEventFactory::orderIdentifier)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        return AllocationValidated.newBuilder()
                .setMetadata(metadata(AllocationValidated.class.getSimpleName(), correlationId, allocation))
                .setAllocId(allocation.getAllocId())
                .setOrderIds(orderIds)
                .setAccountCount(allocation.getAccounts().size())
                .build();
    }

    public ExecutionsAllocated executionsAllocated(Allocation allocation, String correlationId) {
        List<AllocatedExecution> executions = allocation.getAssignments().stream()
                .filter(assignment -> !assignment.isReleased())
                .map(assignment -> AllocatedExecution.newBuilder()
                        .setExecutionId(assignment.getExecutionId())
                        .setQuantity(assignment.getAllocatedQty().setScale(4, RoundingMode.UNNECESSARY))
                        .build())
                .toList();
        return ExecutionsAllocated.newBuilder()
                .setMetadata(metadata(ExecutionsAllocated.class.getSimpleName(), correlationId, allocation))
                .setAllocId(allocation.getAllocId())
                .setExecutions(executions)
                .build();
    }

    public ExecutionsReleased executionsReleased(Allocation allocation, String refAllocId,
                                                 List<String> releasedExecutionIds, String correlationId) {
        return ExecutionsReleased.newBuilder()
                .setMetadata(metadata(ExecutionsReleased.class.getSimpleName(), correlationId, allocation))
                .setAllocId(allocation.getAllocId())
                .setRefAllocId(refAllocId)
                .setReleasedExecutionIds(releasedExecutionIds)
                .build();
    }

    public SettlementRequested settlementRequested(Allocation allocation, String correlationId) {
        List<SettlementAccount> accounts = allocation.getAccounts().stream()
                .map(OrchestrationEventFactory::settlementAccount)
                .toList();
        return SettlementRequested.newBuilder()
                .setMetadata(metadata(SettlementRequested.class.getSimpleName(), correlationId, allocation))
                .setAllocId(allocation.getAllocId())
                .setSettlementDate(allocation.getSettlDate())
                .setAccounts(accounts)
                .build();
    }

    private static SettlementAccount settlementAccount(AllocationAccount account) {
        return SettlementAccount.newBuilder()
                .setAccount(account.getAllocAccount())
                .setQuantity(account.getAllocQty().setScale(4, RoundingMode.UNNECESSARY))
                .setNetMoney(account.getAllocNetMoney() == null ? null
                        : account.getAllocNetMoney().setScale(4, RoundingMode.UNNECESSARY))
                .setCurrency(account.getAllocSettlCurrency())
                .build();
    }

    private static String orderIdentifier(AllocationOrder order) {
        return order.getOrderId() != null ? order.getOrderId() : order.getClOrdId();
    }

    private EventMetadata metadata(String eventType, String correlationId, Allocation allocation) {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setSchemaVersion(EVENT_SCHEMA_VERSION)
                .setOccurredAt(clock.instant())
                .setCorrelationId(correlationId)
                .setCausationId(String.valueOf(allocation.getRawMessageId()))
                .setAggregateId(allocation.getAllocId())
                .build();
    }
}
