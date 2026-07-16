package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.api.dto.InstrumentSummaryResponse;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.exception.ResourceNotFoundException;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.InstrumentService;
import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
class InstrumentControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentService instrumentService;

    private static final String CREATE_BODY = """
            { "instrumentId": "AAPL", "name": "Apple", "instrumentType": "EQUITY",
              "exchangeMic": "XNAS", "currencyCode": "USD",
              "identifiers": [ { "source": "ISIN", "value": "US0378331005" } ] }
            """;

    private static InstrumentResponse response() {
        return new InstrumentResponse("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD",
                ActivationStatus.ACTIVE, List.of(), Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"), 0L);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/instruments/AAPL"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void createRequiresAdminScope() throws Exception {
        mockMvc.perform(post("/api/v1/instruments").with(auth(EnrichmentTestTokens.readerJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createRequiresAdminRoleNotJustTheScope() throws Exception {
        // An INVESTOR carrying enrichment:admin (admin-web default scope) must still be refused:
        // reference-data mutations require realm role ADMIN (global writes need scope + ADMIN).
        var investorWithAdminScope = com.tradingcapture.testsupport.TestJwts.jwt("sub-inv", "investor1",
                java.util.List.of("INVESTOR"), "enrichment:read enrichment:admin",
                java.util.List.of(EnrichmentTestTokens.AUDIENCE));

        mockMvc.perform(post("/api/v1/instruments").with(auth(investorWithAdminScope))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createReturns201() throws Exception {
        when(instrumentService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/instruments").with(auth(EnrichmentTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/instruments/AAPL"))
                .andExpect(jsonPath("$.instrumentId").value("AAPL"));
    }

    @Test
    void createValidationFailureIs400() throws Exception {
        String bad = """
                { "instrumentId": "AAPL", "name": "Apple", "exchangeMic": "toolong", "currencyCode": "usd" }
                """;
        mockMvc.perform(post("/api/v1/instruments").with(auth(EnrichmentTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownInstrumentIs404() throws Exception {
        when(instrumentService.getById("NOPE")).thenThrow(ResourceNotFoundException.instrument("NOPE"));

        mockMvc.perform(get("/api/v1/instruments/NOPE").with(auth(EnrichmentTestTokens.readerJwt())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSTRUMENT_NOT_FOUND"));
    }

    @Test
    void ambiguousIdentifierIs409() throws Exception {
        when(instrumentService.getByIdentifier(eq(IdentifierSource.EXCHANGE_SYMBOL), eq("XYZ"), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.AMBIGUOUS_IDENTIFIER, "ambiguous"));

        mockMvc.perform(get("/api/v1/instruments/by-identifier")
                        .param("source", "EXCHANGE_SYMBOL").param("value", "XYZ")
                        .with(auth(EnrichmentTestTokens.readerJwt())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AMBIGUOUS_IDENTIFIER"));
    }

    @Test
    void searchReturnsPage() throws Exception {
        InstrumentSummaryResponse summary = new InstrumentSummaryResponse("AAPL", "Apple", InstrumentType.EQUITY,
                "XNAS", "USD", ActivationStatus.ACTIVE, Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"), 0L);
        when(instrumentService.search(any(), any())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/instruments").param("instrumentType", "EQUITY")
                        .with(auth(EnrichmentTestTokens.readerJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
