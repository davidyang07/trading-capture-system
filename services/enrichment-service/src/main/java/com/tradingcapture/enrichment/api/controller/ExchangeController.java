package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.ExchangeDtos.CreateExchangeRequest;
import com.tradingcapture.enrichment.api.dto.ExchangeDtos.ExchangeResponse;
import com.tradingcapture.enrichment.api.dto.ExchangeDtos.UpdateExchangeRequest;
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

/** Exchange administration (scope enrichment:admin) and lookups (scope enrichment:read). */
@RestController
@RequestMapping("/api/v1/exchanges")
@Tag(name = "reference")
public class ExchangeController {

    private final ReferenceDataService referenceData;

    public ExchangeController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin') and hasRole('ADMIN')")
    @Operation(summary = "Create an exchange")
    public ResponseEntity<ExchangeResponse> create(@Valid @RequestBody CreateExchangeRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        ExchangeResponse response = referenceData.createExchange(
                request.mic(), request.name(), request.country(), request.timezone());
        return ResponseEntity.created(uriBuilder.path("/api/v1/exchanges/{mic}").build(response.mic())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "List exchanges")
    public List<ExchangeResponse> list() {
        return referenceData.listExchanges();
    }

    @GetMapping("/{mic}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Exchange by MIC")
    public ExchangeResponse get(@PathVariable String mic) {
        return referenceData.getExchange(mic);
    }

    @PutMapping("/{mic}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin') and hasRole('ADMIN')")
    @Operation(summary = "Update an exchange")
    public ExchangeResponse update(@PathVariable String mic, @Valid @RequestBody UpdateExchangeRequest request) {
        return referenceData.updateExchange(mic, request.name(), request.country(), request.timezone());
    }
}
