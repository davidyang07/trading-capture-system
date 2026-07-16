package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure aggregate math over an order's append-only execution history.
 *
 * <p>Model: every TRADE starts a chain; corrections/cancellations extend it via
 * {@code execRefId}. Because each row can be referenced at most once (DB unique constraint),
 * chains are linked lists, and a chain's single unreferenced row is its <em>tip</em>. The
 * chain contributes its tip's {@code lastQty} at {@code lastPx} — unless the tip is a
 * TRADE_CANCEL, which voids the whole chain.</p>
 *
 * <p>cumQty = Σ active-chain contributions; avgPx = Σ(qty·px)/Σqty at scale 8 HALF_UP
 * (null when nothing is filled). Recomputing from the full event list — instead of
 * incrementally patching stored numbers — keeps create/correct/cancel on one code path.</p>
 */
public final class OrderFillMath {

    private OrderFillMath() {
    }

    /** Minimal view of an execution event so hypothetical (not yet persisted) rows fit in. */
    public record FillEvent(String internalId,
                            String execRefId,
                            ExecutionType execType,
                            BigDecimal lastQty,
                            BigDecimal lastPx) {

        public static FillEvent from(Execution execution) {
            return new FillEvent(execution.getInternalId(),
                    execution.getExecRefId(),
                    execution.getExecType(),
                    execution.getLastQty(),
                    execution.getLastPx());
        }
    }

    public record Aggregates(BigDecimal cumQty, BigDecimal avgPx) {
    }

    public static Aggregates compute(List<FillEvent> events) {
        Set<String> referenced = new HashSet<>();
        for (FillEvent event : events) {
            if (event.execRefId() != null) {
                referenced.add(event.execRefId());
            }
        }
        BigDecimal cumQty = BigDecimal.ZERO;
        BigDecimal notional = BigDecimal.ZERO;
        for (FillEvent event : events) {
            boolean isTip = !referenced.contains(event.internalId());
            if (isTip && event.execType() != ExecutionType.TRADE_CANCEL) {
                cumQty = cumQty.add(event.lastQty());
                notional = notional.add(event.lastQty().multiply(event.lastPx()));
            }
        }
        if (cumQty.signum() == 0) {
            return new Aggregates(BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY), null);
        }
        BigDecimal avgPx = notional.divide(cumQty, 8, RoundingMode.HALF_UP);
        return new Aggregates(cumQty, avgPx);
    }

    /** The tip (single unreferenced row) of each chain, cancelled chains included. */
    public static boolean isTip(Execution execution, List<Execution> allOrderExecutions) {
        for (Execution other : allOrderExecutions) {
            if (execution.getInternalId().equals(other.getExecRefId())) {
                return false;
            }
        }
        return true;
    }
}
