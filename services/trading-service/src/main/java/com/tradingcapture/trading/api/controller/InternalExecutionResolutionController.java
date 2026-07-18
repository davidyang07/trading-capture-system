package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.dto.ExecutionResolutionRequest;
import com.tradingcapture.trading.api.dto.ExecutionResolutionResponse;
import com.tradingcapture.trading.domain.service.ExecutionResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-facing execution resolution for the allocation-engine (scope {@code allocation:process}).
 * Resolves external FIX ExecIDs to internal executions + allocation eligibility so the engine can
 * bind by internal id. Read-only; each item resolves independently (a bad id is a per-item
 * NOT_FOUND, not a 4xx). Scope is enforced at the {@code /internal/**} chain and here; investor
 * user tokens (which lack {@code allocation:process}) get 403.
 */
@RestController
@RequestMapping("/internal/v1/executions")
@Tag(name = "allocation-machine", description = "M2M endpoints for allocation-engine (scope allocation:process)")
public class InternalExecutionResolutionController {

    private final ExecutionResolutionService resolutionService;

    public InternalExecutionResolutionController(ExecutionResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAuthority('SCOPE_allocation:process')")
    @Operation(summary = "Resolve external ExecIDs to internal executions + allocation eligibility")
    @ApiResponse(responseCode = "200", description = "Per-item resolutions (NOT_FOUND for unknown ids)")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    public ExecutionResolutionResponse resolve(@Valid @RequestBody ExecutionResolutionRequest request) {
        return ExecutionResolutionResponse.from(resolutionService.resolve(request.execIds()));
    }
}
