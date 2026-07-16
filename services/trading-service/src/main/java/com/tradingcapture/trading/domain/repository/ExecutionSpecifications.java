package com.tradingcapture.trading.domain.repository;

import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Side;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;

/** Null-safe filter fragments for the execution search; services combine the non-null ones. */
public final class ExecutionSpecifications {

    private ExecutionSpecifications() {
    }

    /** Ownership isolation joins through the owning order — never trusted from the client. */
    public static Specification<Execution> orderOwnedBy(String ownerSubject) {
        return (root, query, cb) -> cb.equal(root.get("order").get("ownerSubject"), ownerSubject);
    }

    public static Specification<Execution> hasOrderId(String orderId) {
        return (root, query, cb) -> cb.equal(root.get("order").get("orderId"), orderId);
    }

    public static Specification<Execution> hasExecId(String execId) {
        return (root, query, cb) -> cb.equal(root.get("execId"), execId);
    }

    public static Specification<Execution> hasExecType(ExecutionType execType) {
        return (root, query, cb) -> cb.equal(root.get("execType"), execType);
    }

    public static Specification<Execution> hasSide(Side side) {
        return (root, query, cb) -> cb.equal(root.get("side"), side);
    }

    public static Specification<Execution> hasAllocationState(AllocationState state) {
        return (root, query, cb) -> cb.equal(root.get("allocationState"), state);
    }

    public static Specification<Execution> tradeDateFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
    }

    public static Specification<Execution> tradeDateTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tradeDate"), to);
    }

    public static Specification<Execution> transactTimeFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactTime"), from);
    }

    public static Specification<Execution> transactTimeTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactTime"), to);
    }
}
