package com.tradingcapture.trading.domain.service.command;

import java.util.List;

/**
 * Machine command from the allocation-engine (scope {@code allocation:process}).
 *
 * @param executionInternalIds targets; required for ALLOCATE, optional for RELEASE
 *                             (null releases every execution bound to the allocationId)
 */
public record AllocationStateCommand(String allocationId,
                                     Action action,
                                     List<String> executionInternalIds) {

    public enum Action {
        ALLOCATE,
        RELEASE
    }
}
