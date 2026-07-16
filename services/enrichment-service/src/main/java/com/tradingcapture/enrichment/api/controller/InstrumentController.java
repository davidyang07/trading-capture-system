package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.CreateInstrumentRequest;
import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.api.dto.InstrumentSearchRequest;
import com.tradingcapture.enrichment.api.dto.InstrumentSummaryResponse;
import com.tradingcapture.enrichment.api.dto.PageResponse;
import com.tradingcapture.enrichment.api.dto.UpdateInstrumentRequest;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Instrument administration and lookups. Reads require {@code enrichment:read}; administrative
 * mutations require {@code enrichment:admin}. Lookups are Redis-cached in the service layer.
 */
@RestController
@RequestMapping("/api/v1/instruments")
@Validated
@Tag(name = "instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Create an instrument with its alternate identifiers")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failure or unknown exchange/currency")
    @ApiResponse(responseCode = "409", description = "Duplicate instrument ID or identifier")
    public ResponseEntity<InstrumentResponse> create(@Valid @RequestBody CreateInstrumentRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        InstrumentResponse response = instrumentService.create(request.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/instruments/{id}").build(response.instrumentId()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Search instruments (deterministic sort with instrumentId tiebreak)")
    public PageResponse<InstrumentSummaryResponse> search(@ParameterObject @Valid InstrumentSearchRequest request) {
        Page<InstrumentSummaryResponse> page = instrumentService.search(request.toCriteria(), request.toPageable());
        return PageResponse.of(page, summary -> summary);
    }

    @GetMapping("/by-identifier")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Resolve an instrument by an alternate identifier (exchange disambiguates a symbol)")
    @ApiResponse(responseCode = "200", description = "Instrument")
    @ApiResponse(responseCode = "404", description = "Unknown identifier")
    @ApiResponse(responseCode = "409", description = "Ambiguous identifier (matches multiple instruments)")
    public InstrumentResponse getByIdentifier(@RequestParam IdentifierSource source,
                                              @RequestParam @NotBlank String value,
                                              @RequestParam(required = false) String exchange) {
        return instrumentService.getByIdentifier(source, value, exchange);
    }

    @GetMapping("/{instrumentId}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:read')")
    @Operation(summary = "Instrument detail including alternate identifiers")
    @ApiResponse(responseCode = "200", description = "Instrument")
    @ApiResponse(responseCode = "404", description = "Unknown")
    public InstrumentResponse get(@PathVariable String instrumentId) {
        return instrumentService.getById(instrumentId);
    }

    @PutMapping("/{instrumentId}")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Update an instrument (replaces mutable fields and identifiers)")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "Unknown")
    public InstrumentResponse update(@PathVariable String instrumentId,
                                     @Valid @RequestBody UpdateInstrumentRequest request) {
        return instrumentService.update(instrumentId, request.toCommand());
    }

    @PostMapping("/{instrumentId}/activate")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Activate an instrument")
    public InstrumentResponse activate(@PathVariable String instrumentId) {
        return instrumentService.setStatus(instrumentId, ActivationStatus.ACTIVE);
    }

    @PostMapping("/{instrumentId}/deactivate")
    @PreAuthorize("hasAuthority('SCOPE_enrichment:admin')")
    @Operation(summary = "Deactivate an instrument")
    public InstrumentResponse deactivate(@PathVariable String instrumentId) {
        return instrumentService.setStatus(instrumentId, ActivationStatus.INACTIVE);
    }
}
