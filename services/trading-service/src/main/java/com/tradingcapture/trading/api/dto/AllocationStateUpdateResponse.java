package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.model.AllocationState;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;

import java.util.List;

/**
 * Post-transition snapshot. {@code appliedCount} counts actual state changes — an idempotent
 * replay returns 200 with appliedCount 0.
 */
public record AllocationStateUpdateResponse(String allocationId,
                                            AllocationStateCommand.Action action,
                                            int appliedCount,
                                            List<ExecutionAllocationView> executions) {

    public record ExecutionAllocationView(String internalId,
                                          AllocationState allocationState,
                                          String allocationId) {
    }
}
