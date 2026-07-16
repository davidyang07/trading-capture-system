package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.CreateFeeRuleRequest;
import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.FeeRuleResponse;
import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.UpdateFeeRuleRequest;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.FeeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Fee-rule administration (scope enrichment:admin) and lookup (scope enrichment:read). The
 * applicable-rule lookup serves later trading/allocation fee processing; this service does not
 * compute fees.
 */
@RestController
@RequestMapping("/api/v1/fee-rules")
@Tag(name = "fee-rules")
public class FeeRuleController {

    private final FeeRuleService feeRuleService;

    public FeeRuleController(FeeRuleService feeRuleService) {
        this.feeRuleService = feeRuleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Create a fee rule")
    public ResponseEntity<FeeRuleResponse> create(@Valid @RequestBody CreateFeeRuleRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        FeeRuleResponse response = feeRuleService.create(request.toCommand());
        return ResponseEntity.created(uriBuilder.path("/api/v1/fee-rules/{id}").build(response.feeRuleId())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "List all fee rules")
    public List<FeeRuleResponse> list() {
        return feeRuleService.listAll();
    }

    @GetMapping("/applicable")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Active fee rules applicable to a trade with the given (optional) dimensions")
    public List<FeeRuleResponse> applicable(@RequestParam(required = false) String exchange,
                                            @RequestParam(required = false) InstrumentType instrumentType,
                                            @RequestParam(required = false) String currency) {
        return feeRuleService.findApplicable(exchange, instrumentType, currency);
    }

    @GetMapping("/{feeRuleId}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Fee rule by ID")
    public FeeRuleResponse get(@PathVariable String feeRuleId) {
        return feeRuleService.getById(feeRuleId);
    }

    @PutMapping("/{feeRuleId}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Update a fee rule")
    public FeeRuleResponse update(@PathVariable String feeRuleId, @Valid @RequestBody UpdateFeeRuleRequest request) {
        return feeRuleService.update(feeRuleId, request.toCommand());
    }

    @PostMapping("/{feeRuleId}/activate")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Activate a fee rule")
    public FeeRuleResponse activate(@PathVariable String feeRuleId) {
        return feeRuleService.setStatus(feeRuleId, ActivationStatus.ACTIVE);
    }

    @PostMapping("/{feeRuleId}/deactivate")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Deactivate a fee rule")
    public FeeRuleResponse deactivate(@PathVariable String feeRuleId) {
        return feeRuleService.setStatus(feeRuleId, ActivationStatus.INACTIVE);
    }
}
