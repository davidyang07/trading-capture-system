package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.dto.CancelExecutionRequest;
import com.tradingcapture.trading.api.dto.CorrectExecutionRequest;
import com.tradingcapture.trading.api.dto.CreateExecutionRequest;
import com.tradingcapture.trading.api.dto.ExecutionDetailResponse;
import com.tradingcapture.trading.api.dto.ExecutionResponse;
import com.tradingcapture.trading.api.dto.ExecutionSearchRequest;
import com.tradingcapture.trading.api.dto.PageResponse;
import com.tradingcapture.trading.api.mapper.ExecutionMapper;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.domain.service.ExecutionQueryService;
import com.tradingcapture.trading.domain.service.ExecutionService;
import com.tradingcapture.trading.security.AccessContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Execution capture, search, detail, and the append-only correct/cancel amendments.
 * Writes require the execution:write scope AND the ADMIN role (execution capture is an
 * operational function; investor tokens carry the scope but not the role). Reads require
 * execution:read with investor ownership isolation in the service layer.
 */
@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "executions")
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionQueryService executionQueryService;
    private final ExecutionMapper mapper;
    private final AccessContextFactory accessContextFactory;

    public ExecutionController(ExecutionService executionService,
                               ExecutionQueryService executionQueryService,
                               ExecutionMapper mapper,
                               AccessContextFactory accessContextFactory) {
        this.executionService = executionService;
        this.executionQueryService = executionQueryService;
        this.mapper = mapper;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_execution:write') and hasRole('ADMIN')")
    @Operation(summary = "Capture a trade execution (server derives cumQty/leavesQty/avgPx and order status)")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "404", description = "Order unknown")
    @ApiResponse(responseCode = "409", description = "Order filled, overfill, or duplicate execId")
    public ResponseEntity<ExecutionResponse> create(Authentication authentication,
                                                    @Valid @RequestBody CreateExecutionRequest request,
                                                    UriComponentsBuilder uriBuilder) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Execution execution = executionService.createTrade(ctx, request.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/executions/{internalId}").build(execution.getInternalId()))
                .body(mapper.toResponse(execution));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_execution:read')")
    @Operation(summary = "Search executions (investors: own orders only; deterministic sort with internalId tiebreak)")
    public PageResponse<ExecutionResponse> search(Authentication authentication,
                                                  @ParameterObject @Valid ExecutionSearchRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Page<Execution> page = executionQueryService.search(ctx, request.toCriteria(), request.toPageable());
        return PageResponse.of(page, mapper::toResponse);
    }

    @GetMapping("/{internalId}")
    @PreAuthorize("hasAuthority('SCOPE_execution:read')")
    @Operation(summary = "Execution detail with fees and the full correction/cancellation chain")
    @ApiResponse(responseCode = "200", description = "Execution")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    public ExecutionDetailResponse get(Authentication authentication, @PathVariable String internalId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toDetailResponse(executionQueryService.getDetail(ctx, internalId));
    }

    @PostMapping("/{internalId}/correct")
    @PreAuthorize("hasAuthority('SCOPE_execution:write') and hasRole('ADMIN')")
    @Operation(summary = "Correct an execution — appends a linked TRADE_CORRECT record; the target stays immutable")
    @ApiResponse(responseCode = "201", description = "Correction appended")
    @ApiResponse(responseCode = "404", description = "Target unknown")
    @ApiResponse(responseCode = "409", description = "Target superseded/cancelled/allocated, or overfill")
    public ResponseEntity<ExecutionResponse> correct(Authentication authentication,
                                                     @PathVariable String internalId,
                                                     @Valid @RequestBody CorrectExecutionRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Execution correction = executionService.correct(ctx, internalId, request.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/executions/{internalId}").build(correction.getInternalId()))
                .body(mapper.toResponse(correction));
    }

    @PostMapping("/{internalId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_execution:write') and hasRole('ADMIN')")
    @Operation(summary = "Cancel an execution — appends a linked TRADE_CANCEL record; the target stays immutable")
    @ApiResponse(responseCode = "201", description = "Cancellation appended")
    @ApiResponse(responseCode = "404", description = "Target unknown")
    @ApiResponse(responseCode = "409", description = "Target superseded/cancelled/allocated")
    public ResponseEntity<ExecutionResponse> cancel(Authentication authentication,
                                                    @PathVariable String internalId,
                                                    @Valid @RequestBody(required = false) CancelExecutionRequest request,
                                                    UriComponentsBuilder uriBuilder) {
        AccessContext ctx = accessContextFactory.from(authentication);
        CancelExecutionRequest body = request != null ? request : CancelExecutionRequest.empty();
        Execution cancellation = executionService.cancel(ctx, internalId, body.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/executions/{internalId}").build(cancellation.getInternalId()))
                .body(mapper.toResponse(cancellation));
    }
}
