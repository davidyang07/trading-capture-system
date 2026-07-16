package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.dto.AllocationStateUpdateRequest;
import com.tradingcapture.trading.api.dto.AllocationStateUpdateResponse;
import com.tradingcapture.trading.domain.service.AllocationStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-facing endpoint for the allocation-engine (scope {@code allocation:process}).
 * Idempotent by contract (ADR-0006/0007): replays return 200 with appliedCount 0; binding
 * an execution to a different allocation is a 409 conflict.
 */
@RestController
@RequestMapping("/internal/v1/executions")
@Tag(name = "allocation-machine", description = "M2M endpoints for allocation-engine (scope allocation:process)")
public class AllocationStateController {

    private final AllocationStateService allocationStateService;

    public AllocationStateController(AllocationStateService allocationStateService) {
        this.allocationStateService = allocationStateService;
    }

    @PutMapping("/allocation-state")
    @PreAuthorize("hasAuthority('SCOPE_allocation:process')")
    @Operation(summary = "Idempotently mark executions allocated/released for an allocationId")
    @ApiResponse(responseCode = "200", description = "Applied (appliedCount 0 on an idempotent replay)")
    @ApiResponse(responseCode = "404", description = "An execution ID is unknown")
    @ApiResponse(responseCode = "409", description = "Conflicting allocation state or ineligible target")
    public AllocationStateUpdateResponse apply(@Valid @RequestBody AllocationStateUpdateRequest request) {
        AllocationStateService.Result result = allocationStateService.apply(request.toCommand());
        return new AllocationStateUpdateResponse(result.allocationId(),
                result.action(),
                result.appliedCount(),
                result.executions().stream()
                        .map(execution -> new AllocationStateUpdateResponse.ExecutionAllocationView(
                                execution.getInternalId(),
                                execution.getAllocationState(),
                                execution.getAllocationId()))
                        .toList());
    }
}
