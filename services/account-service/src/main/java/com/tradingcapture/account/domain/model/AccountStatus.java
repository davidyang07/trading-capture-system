package com.tradingcapture.account.domain.model;

import java.util.Set;

/**
 * Account lifecycle state. Legal transitions are declared here so the aggregate and its tests
 * share one source of truth:
 *
 * <pre>
 *   PENDING   → ACTIVE, CLOSED
 *   ACTIVE    → SUSPENDED, CLOSED
 *   SUSPENDED → ACTIVE, CLOSED
 *   CLOSED    → (terminal)
 * </pre>
 *
 * Only {@link #ACTIVE} accounts may post ledger transactions; {@link #CLOSED} additionally
 * requires every balance to be zero (enforced in the service).
 */
public enum AccountStatus implements CodeEnum {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED;

    private static final java.util.Map<AccountStatus, Set<AccountStatus>> TRANSITIONS = java.util.Map.of(
            PENDING, Set.of(ACTIVE, CLOSED),
            ACTIVE, Set.of(SUSPENDED, CLOSED),
            SUSPENDED, Set.of(ACTIVE, CLOSED),
            CLOSED, Set.of());

    public boolean canTransitionTo(AccountStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    @Override
    public String code() {
        return name();
    }
}
