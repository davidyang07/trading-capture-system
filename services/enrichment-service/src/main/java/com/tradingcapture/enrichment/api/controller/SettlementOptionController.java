package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.SettlementOptionDtos.CreateSettlementOptionRequest;
import com.tradingcapture.enrichment.api.dto.SettlementOptionDtos.SettlementOptionResponse;
import com.tradingcapture.enrichment.api.dto.SettlementOptionDtos.UpdateSettlementOptionRequest;
import com.tradingcapture.enrichment.domain.service.ReferenceDataService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Settlement-option administration (scope enrichment:admin) and lookups (scope enrichment:read). */
@RestController
@RequestMapping("/api/v1/settlement-options")
@Tag(name = "reference")
public class SettlementOptionController {

    private final ReferenceDataService referenceData;

    public SettlementOptionController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Create a settlement option")
    public ResponseEntity<SettlementOptionResponse> create(@Valid @RequestBody CreateSettlementOptionRequest request,
                                                           UriComponentsBuilder uriBuilder) {
        SettlementOptionResponse response = referenceData.createSettlementOption(
                request.code(), request.name(), request.settlementDays(), request.description());
        return ResponseEntity.created(uriBuilder.path("/api/v1/settlement-options/{code}").build(response.code())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "List settlement options")
    public List<SettlementOptionResponse> list() {
        return referenceData.listSettlementOptions();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Settlement option by code")
    public SettlementOptionResponse get(@PathVariable String code) {
        return referenceData.getSettlementOption(code);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Update a settlement option")
    public SettlementOptionResponse update(@PathVariable String code,
                                           @Valid @RequestBody UpdateSettlementOptionRequest request) {
        return referenceData.updateSettlementOption(code, request.name(), request.settlementDays(), request.description());
    }
}
