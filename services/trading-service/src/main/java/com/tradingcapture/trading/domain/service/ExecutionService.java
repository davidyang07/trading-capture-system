package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionMiscFee;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.repository.OrderRepository;
import com.tradingcapture.trading.domain.service.OrderFillMath.Aggregates;
import com.tradingcapture.trading.domain.service.OrderFillMath.FillEvent;
import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import com.tradingcapture.trading.observability.TradingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution capture and the append-only correction/cancellation chains.
 *
 * <p>Chain rules:</p>
 * <ul>
 *   <li>Only the tip of a chain may be amended: a record that is already referenced by a
 *       correction/cancellation is superseded and immutable forever (also enforced by the
 *       DB unique constraint on {@code exec_ref_id}, which additionally forbids branching
 *       and cycles).</li>
 *   <li>A cancellation cannot itself be corrected or cancelled.</li>
 *   <li>An execution currently bound to an allocation must be released by the
 *       allocation-engine before it can be amended.</li>
 *   <li>{@code rootExecutionId} is resolved at write time: the target's root, or the target
 *       itself when it starts a chain (persisted on every row so chains stay fully queryable).</li>
 *   <li>cumQty / leavesQty / avgPx / order status are recomputed server-side from the active
 *       chain state inside the same transaction; the client's view of them is never trusted.
 *       The order row carries {@code @Version}, so two concurrent captures cannot both
 *       commit against the same aggregate state.</li>
 * </ul>
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final OrderRepository orders;
    private final ExecutionRepository executions;
    private final TradingMetrics metrics;
    private final Clock clock;

    public ExecutionService(OrderRepository orders,
                            ExecutionRepository executions,
                            TradingMetrics metrics,
                            Clock clock) {
        this.orders = orders;
        this.executions = executions;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Execution createTrade(AccessContext ctx, ExecutionCommands.CreateTrade command) {
        Order order = orders.findById(command.orderId())
                .orElseThrow(() -> ResourceNotFoundException.order(command.orderId()));
        ctx.requireVisible(order);
        if (order.isFilled()) {
            throw new BusinessRuleException(ErrorCode.ORDER_ALREADY_FILLED,
                    "Order " + order.getOrderId() + " is already filled");
        }
        BigDecimal lastQty = command.lastQty().setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal lastPx = command.lastPx().setScale(8, RoundingMode.UNNECESSARY);
        if (lastQty.compareTo(order.getLeavesQty()) > 0) {
            throw new BusinessRuleException(ErrorCode.EXECUTION_OVERFILL,
                    "lastQty " + lastQty + " exceeds leaves quantity " + order.getLeavesQty()
                            + " of order " + order.getOrderId());
        }

        Instant now = Instant.now(clock);
        String internalId = TradingIds.newExecutionInternalId();
        String execId = resolveExecId(command.execId());

        List<Execution> history = executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc(order.getOrderId());
        Aggregates aggregates = recompute(history,
                new FillEvent(internalId, null, ExecutionType.TRADE, lastQty, lastPx));
        order.applyFillState(aggregates.cumQty(), now);

        Execution execution = Execution.builder()
                .internalId(internalId)
                .execId(execId)
                .order(order)
                .execType(ExecutionType.TRADE)
                .execRefId(null)
                .rootExecutionId(internalId) // a TRADE starts its own chain
                .ordStatus(order.getStatus())
                .side(order.getSide())
                .lastQty(lastQty)
                .lastPx(lastPx)
                .cumQty(aggregates.cumQty())
                .leavesQty(order.getLeavesQty())
                .avgPx(aggregates.avgPx())
                .currency(order.getCurrency())
                .tradeDate(command.tradeDate() != null ? command.tradeDate() : LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                .transactTime(command.transactTime() != null ? command.transactTime() : now)
                .settleType(order.getSettleType())
                .settleDate(order.getSettleDate())
                .text(command.text())
                .createdAt(now)
                .build();
        attachFees(execution, command.miscFees(), now);

        executions.save(execution);
        metrics.executionRecorded(ExecutionType.TRADE);
        log.info("Execution recorded: internalId={} execType=TRADE orderId={} orderStatus={}",
                execution.getInternalId(), order.getOrderId(), order.getStatus());
        return execution;
    }

    @Transactional
    public Execution correct(AccessContext ctx, String targetInternalId, ExecutionCommands.Correct command) {
        Execution target = loadAmendableTarget(ctx, targetInternalId);
        Order order = target.getOrder();

        BigDecimal lastQty = command.lastQty().setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal lastPx = command.lastPx().setScale(8, RoundingMode.UNNECESSARY);
        // The target is its chain's active tip, so its lastQty is currently part of cumQty.
        BigDecimal newCum = order.getCumQty().subtract(target.getLastQty()).add(lastQty);
        if (newCum.compareTo(order.getQuantity()) > 0) {
            throw new BusinessRuleException(ErrorCode.EXECUTION_OVERFILL,
                    "Corrected quantity would overfill order " + order.getOrderId()
                            + " (cumQty " + newCum + " > quantity " + order.getQuantity() + ")");
        }

        Instant now = Instant.now(clock);
        String internalId = TradingIds.newExecutionInternalId();
        Execution correction = appendAmendment(target, ExecutionType.TRADE_CORRECT, internalId,
                resolveExecId(command.execId()), lastQty, lastPx, command.text(), now);

        if (command.miscFees() == null) {
            // A correction restates the trade; without an explicit fee set it keeps the target's.
            for (ExecutionMiscFee fee : target.getMiscFees()) {
                correction.addMiscFee(new ExecutionMiscFee(correction, fee.getFeeType(), fee.getAmount(),
                        fee.getCurrency(), fee.getBasis(), now));
            }
        } else {
            attachFees(correction, command.miscFees(), now);
        }

        executions.save(correction);
        metrics.executionRecorded(ExecutionType.TRADE_CORRECT);
        log.info("Execution corrected: target={} correction={} orderId={} orderStatus={}",
                targetInternalId, correction.getInternalId(), order.getOrderId(), order.getStatus());
        return correction;
    }

    @Transactional
    public Execution cancel(AccessContext ctx, String targetInternalId, ExecutionCommands.Cancel command) {
        Execution target = loadAmendableTarget(ctx, targetInternalId);
        Order order = target.getOrder();

        Instant now = Instant.now(clock);
        String internalId = TradingIds.newExecutionInternalId();
        Execution cancellation = appendAmendment(target, ExecutionType.TRADE_CANCEL, internalId,
                resolveExecId(command.execId()), BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),
                null, command.text(), now);

        executions.save(cancellation);
        metrics.executionRecorded(ExecutionType.TRADE_CANCEL);
        log.info("Execution cancelled: target={} cancellation={} orderId={} orderStatus={}",
                targetInternalId, cancellation.getInternalId(), order.getOrderId(), order.getStatus());
        return cancellation;
    }

    /**
     * Loads the amendment target under a row lock and enforces chain eligibility. Missing targets
     * and targets on another investor's order surface identically as 404.
     */
    private Execution loadAmendableTarget(AccessContext ctx, String targetInternalId) {
        // Locked so a concurrent ALLOCATE of the same execution cannot pass its eligibility check
        // against our not-yet-committed amendment (see AllocationStateService).
        Execution target = executions.findAllByInternalIdInForUpdate(List.of(targetInternalId)).stream()
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.execution(targetInternalId));
        ctx.requireVisible(target.getOrder());
        if (target.isCancellation()) {
            throw new BusinessRuleException(ErrorCode.EXECUTION_TARGET_CANCELLED,
                    "Execution " + targetInternalId + " is a cancellation and cannot be amended");
        }
        if (executions.existsByExecRefId(targetInternalId)) {
            throw new BusinessRuleException(ErrorCode.EXECUTION_TARGET_SUPERSEDED,
                    "Execution " + targetInternalId + " was already corrected or cancelled");
        }
        if (target.isAllocated()) {
            throw new BusinessRuleException(ErrorCode.EXECUTION_ALLOCATED,
                    "Execution " + targetInternalId + " is allocated (" + target.getAllocationId()
                            + ") and must be released before amendment");
        }
        return target;
    }

    /**
     * Builds the append-only amendment row and applies the recomputed aggregate state to the
     * order in the same transaction. The historical target row is never modified.
     */
    private Execution appendAmendment(Execution target,
                                      ExecutionType execType,
                                      String internalId,
                                      String execId,
                                      BigDecimal lastQty,
                                      BigDecimal lastPx,
                                      String text,
                                      Instant now) {
        Order order = target.getOrder();
        // Safe root resolution: the target's root, or the target itself if it started the
        // chain. Persisted on every row (never null).
        String rootExecutionId = target.getRootExecutionId() != null
                ? target.getRootExecutionId()
                : target.getInternalId();

        List<Execution> history = executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc(order.getOrderId());
        Aggregates aggregates = recompute(history,
                new FillEvent(internalId, target.getInternalId(), execType, lastQty, lastPx));
        order.applyFillState(aggregates.cumQty(), now);

        return Execution.builder()
                .internalId(internalId)
                .execId(execId)
                .order(order)
                .execType(execType)
                .execRefId(target.getInternalId())
                .rootExecutionId(rootExecutionId)
                .ordStatus(order.getStatus())
                .side(target.getSide())
                .lastQty(lastQty)
                .lastPx(lastPx)
                .cumQty(aggregates.cumQty())
                .leavesQty(order.getLeavesQty())
                .avgPx(aggregates.avgPx())
                .currency(target.getCurrency())
                // A correction or cancellation amends the same trade: it keeps the trade's date
                // (and settlement terms); only transactTime records when the amendment happened.
                .tradeDate(target.getTradeDate())
                .transactTime(now)
                .settleType(target.getSettleType())
                .settleDate(target.getSettleDate())
                .text(text)
                .createdAt(now)
                .build();
    }

    private Aggregates recompute(List<Execution> history, FillEvent pendingEvent) {
        List<FillEvent> events = new ArrayList<>(history.size() + 1);
        for (Execution execution : history) {
            events.add(FillEvent.from(execution));
        }
        events.add(pendingEvent);
        return OrderFillMath.compute(events);
    }

    private String resolveExecId(String requested) {
        if (!StringUtils.hasText(requested)) {
            return TradingIds.newExecId();
        }
        if (executions.existsByExecId(requested)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_EXEC_ID,
                    "execId already in use: " + requested);
        }
        return requested;
    }

    private void attachFees(Execution execution, List<ExecutionCommands.MiscFee> fees, Instant now) {
        if (fees == null) {
            return;
        }
        for (ExecutionCommands.MiscFee fee : fees) {
            execution.addMiscFee(new ExecutionMiscFee(execution,
                    fee.feeType(),
                    fee.amount().setScale(4, RoundingMode.UNNECESSARY),
                    fee.currency(),
                    fee.basis(),
                    now));
        }
    }
}
