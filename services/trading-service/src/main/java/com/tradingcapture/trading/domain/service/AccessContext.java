package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Order;

/**
 * Authenticated caller identity as the domain sees it, derived exclusively from the verified
 * JWT by the security layer — never from request content.
 *
 * @param subject     owner subject: the token's {@code preferred_username} claim when present
 *                    (matches the deterministic Keycloak dev-realm usernames and seed data),
 *                    otherwise the {@code sub} claim
 * @param admin       caller holds realm role ADMIN
 * @param machineRead caller holds the machine scope {@code allocation:process}
 *                    (allocation-engine), which grants read access across owners
 */
public record AccessContext(String subject, boolean admin, boolean machineRead) {

    public boolean canAccessAllOrders() {
        return admin || machineRead;
    }

    /** True when this caller may see the given order (owner or privileged). */
    public boolean canSee(Order order) {
        return canAccessAllOrders() || order.isOwnedBy(subject);
    }

    /**
     * Ownership gate: non-privileged callers may see only their own orders. Denials raise
     * the same not-found error as a missing resource so unowned resource existence is
     * never revealed (404 masking). Masks as an order-not-found — callers reaching an
     * execution through it should mask as the resource the client actually requested.
     */
    public void requireVisible(Order order) {
        if (!canSee(order)) {
            throw ResourceNotFoundException.order(order.getOrderId());
        }
    }
}
