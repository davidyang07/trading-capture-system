package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.repository.ExecutionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution reads: detail (including the full correction/cancellation chain) and search.
 * Ownership isolation follows the order's owner; privileged callers (ADMIN, machine scope)
 * see everything.
 */
@Service
public class ExecutionQueryService {

    /** An execution plus its complete chain in write order (root first, tip last). */
    public record ExecutionWithChain(Execution execution, List<Execution> chain) {

        /** The single row of this chain that no other row references. */
        public Execution activeTip() {
            List<String> referenced = chain.stream().map(Execution::getExecRefId).toList();
            return chain.stream()
                    .filter(row -> !referenced.contains(row.getInternalId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Chain of " + execution.getInternalId() + " has no tip — data corruption"));
        }
    }

    private final ExecutionRepository executions;

    public ExecutionQueryService(ExecutionRepository executions) {
        this.executions = executions;
    }

    @Transactional(readOnly = true)
    public ExecutionWithChain getDetail(AccessContext ctx, String internalId) {
        Execution execution = executions.findById(internalId)
                .orElseThrow(() -> ResourceNotFoundException.execution(internalId));
        // Mask an unowned execution as the requested resource type (execution-not-found),
        // so the errorCode never lets a caller distinguish "exists but not yours" from
        // "does not exist" — a genuinely missing execution throws the same code above.
        if (!ctx.canSee(execution.getOrder())) {
            throw ResourceNotFoundException.execution(internalId);
        }
        // Open-in-view is disabled: initialize the lazy fee collection inside this
        // transaction so the API layer can map it after the session is gone.
        execution.getMiscFees();
        List<Execution> chain = executions
                .findByRootExecutionIdOrderByCreatedAtAscInternalIdAsc(execution.getRootExecutionId());
        return new ExecutionWithChain(execution, chain);
    }

    @Transactional(readOnly = true)
    public Page<Execution> search(AccessContext ctx, ExecutionSearchCriteria criteria, Pageable pageable) {
        List<Specification<Execution>> parts = new ArrayList<>();
        if (!ctx.canAccessAllOrders()) {
            parts.add(ExecutionSpecifications.orderOwnedBy(ctx.subject()));
        }
        if (StringUtils.hasText(criteria.orderId())) {
            parts.add(ExecutionSpecifications.hasOrderId(criteria.orderId()));
        }
        if (StringUtils.hasText(criteria.execId())) {
            parts.add(ExecutionSpecifications.hasExecId(criteria.execId()));
        }
        if (criteria.execType() != null) {
            parts.add(ExecutionSpecifications.hasExecType(criteria.execType()));
        }
        if (criteria.side() != null) {
            parts.add(ExecutionSpecifications.hasSide(criteria.side()));
        }
        if (criteria.allocationState() != null) {
            parts.add(ExecutionSpecifications.hasAllocationState(criteria.allocationState()));
        }
        if (criteria.tradeDateFrom() != null) {
            parts.add(ExecutionSpecifications.tradeDateFrom(criteria.tradeDateFrom()));
        }
        if (criteria.tradeDateTo() != null) {
            parts.add(ExecutionSpecifications.tradeDateTo(criteria.tradeDateTo()));
        }
        if (criteria.transactTimeFrom() != null) {
            parts.add(ExecutionSpecifications.transactTimeFrom(criteria.transactTimeFrom()));
        }
        if (criteria.transactTimeTo() != null) {
            parts.add(ExecutionSpecifications.transactTimeTo(criteria.transactTimeTo()));
        }
        return executions.findAll(Specification.allOf(parts), pageable);
    }

    /**
     * Executions of one order in write order — used by the order detail endpoint. Takes the
     * already-authorized {@link Order} aggregate (not a raw id) so this read cannot be reached
     * without the caller first passing the order's ownership gate.
     */
    @Transactional(readOnly = true)
    public List<Execution> findByOrder(Order order) {
        return executions.findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc(order.getOrderId());
    }

    /** Per-row chain view: true when another row references this one (superseded). */
    public static boolean isSuperseded(Execution execution, List<Execution> sameOrderRows) {
        return !OrderFillMath.isTip(execution, sameOrderRows);
    }
}
