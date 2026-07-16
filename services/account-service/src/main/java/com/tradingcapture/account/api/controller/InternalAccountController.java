package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.api.dto.AccountBatchValidationRequest;
import com.tradingcapture.account.api.dto.AccountBatchValidationResponse;
import com.tradingcapture.account.domain.service.validation.AccountValidationService;
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
 * Machine-facing batch account validation for allocation-engine and trading-service. Read-only
 * and side-effect-free; each item gets an independent verdict. Requires a dedicated machine
 * scope ({@code account:validate} or {@code allocation:process}) — enforced both at the filter
 * chain ({@code /internal/**}) and here for defense in depth. That scope is not granted to the
 * admin-web user client, so investor user tokens cannot reach this cross-owner endpoint (which
 * does not apply investor ownership masking).
 */
@RestController
@RequestMapping("/internal/v1/accounts")
@Tag(name = "machine")
public class InternalAccountController {

    private final AccountValidationService validationService;

    public InternalAccountController(AccountValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyAuthority('SCOPE_account:validate', 'SCOPE_allocation:process')")
    @Operation(summary = "Batch account existence/status/sufficiency validation (per-item results)")
    @ApiResponse(responseCode = "200", description = "Per-item verdicts")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    public AccountBatchValidationResponse validate(@Valid @RequestBody AccountBatchValidationRequest request) {
        return AccountBatchValidationResponse.from(validationService.validate(request.toChecks()));
    }
}
