package com.tradingcapture.trading.domain.repository;

import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.Side;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Null-safe filter fragments for the order search; services combine the non-null ones. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> ownedBy(String ownerSubject) {
        return (root, query, cb) -> cb.equal(root.get("ownerSubject"), ownerSubject);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasSide(Side side) {
        return (root, query, cb) -> cb.equal(root.get("side"), side);
    }

    public static Specification<Order> hasInstrumentId(String instrumentId) {
        return (root, query, cb) -> cb.equal(root.get("instrumentId"), instrumentId);
    }

    public static Specification<Order> hasAccountId(String accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Order> settleDateFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("settleDate"), from);
    }

    public static Specification<Order> settleDateTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("settleDate"), to);
    }
}
