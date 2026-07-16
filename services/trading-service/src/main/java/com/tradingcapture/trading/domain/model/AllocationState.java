package com.tradingcapture.trading.domain.model;

/**
 * Allocation state of a single execution, owned by trading-service and mutated only through
 * the machine API used by the allocation-engine (Phase 7/8). This is deliberately a
 * two-state machine: ALLOCATE binds an execution to one allocation identifier, RELEASE
 * (Replace/Cancel flows) returns it to the free pool. Idempotency and conflict semantics
 * live in the service; this enum owns which transitions exist at all.
 */
public enum AllocationState {
    UNALLOCATED("UNALLOCATED"),
    ALLOCATED("ALLOCATED");

    private final String code;

    AllocationState(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** ALLOCATE moves UNALLOCATED->ALLOCATED; RELEASE moves ALLOCATED->UNALLOCATED. */
    public boolean canTransitionTo(AllocationState target) {
        return this != target;
    }

    public static AllocationState fromCode(String code) {
        for (AllocationState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown AllocationState code: " + code);
    }
}
