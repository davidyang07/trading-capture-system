package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.InstrumentBatchLookupRequest;
import com.tradingcapture.enrichment.api.dto.InstrumentBatchLookupResponse;
import com.tradingcapture.enrichment.domain.service.InstrumentService;
import com.tradingcapture.enrichment.domain.service.InstrumentService.BatchResolution;
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
 * Machine-facing batch instrument resolution for allocation-engine and trading-service. Requires
 * a service scope ({@code enrichment:read} or {@code allocation:process}) — enforced both at the
 * filter chain ({@code /internal/**}) and here. Read-only; each item resolves independently.
 */
@RestController
@RequestMapping("/internal/v1/instruments")
@Tag(name = "machine")
public class InternalInstrumentController {

    private final InstrumentService instrumentService;

    public InternalInstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('SCOPE_enrichment:read', 'SCOPE_allocation:process')")
    @Operation(summary = "Batch resolve instruments by alternate identifier (per-item results)")
    @ApiResponse(responseCode = "200", description = "Per-item resolutions")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    public InstrumentBatchLookupResponse lookup(@Valid @RequestBody InstrumentBatchLookupRequest request) {
        return new InstrumentBatchLookupResponse(instrumentService.resolveBatch(request.toKeys()).stream()
                .map(this::toResult)
                .toList());
    }

    private InstrumentBatchLookupResponse.Result toResult(BatchResolution resolution) {
        return new InstrumentBatchLookupResponse.Result(
                resolution.key().source(),
                resolution.key().value(),
                resolution.key().exchangeMic(),
                resolution.found(),
                resolution.ambiguous(),
                resolution.instrument());
    }
}
