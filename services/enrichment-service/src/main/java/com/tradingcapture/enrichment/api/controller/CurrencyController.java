package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.CurrencyDtos.CreateCurrencyRequest;
import com.tradingcapture.enrichment.api.dto.CurrencyDtos.CurrencyResponse;
import com.tradingcapture.enrichment.api.dto.CurrencyDtos.UpdateCurrencyRequest;
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

/** Currency administration (scope enrichment:admin) and lookups (scope enrichment:read). */
@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "reference")
public class CurrencyController {

    private final ReferenceDataService referenceData;

    public CurrencyController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin') and hasRole('ADMIN')")
    @Operation(summary = "Create a currency")
    public ResponseEntity<CurrencyResponse> create(@Valid @RequestBody CreateCurrencyRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        CurrencyResponse response = referenceData.createCurrency(request.code(), request.name(), request.minorUnits());
        return ResponseEntity.created(uriBuilder.path("/api/v1/currencies/{code}").build(response.code())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "List currencies")
    public List<CurrencyResponse> list() {
        return referenceData.listCurrencies();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Currency by code")
    public CurrencyResponse get(@PathVariable String code) {
        return referenceData.getCurrency(code);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin') and hasRole('ADMIN')")
    @Operation(summary = "Update a currency")
    public CurrencyResponse update(@PathVariable String code, @Valid @RequestBody UpdateCurrencyRequest request) {
        return referenceData.updateCurrency(code, request.name(), request.minorUnits());
    }
}
