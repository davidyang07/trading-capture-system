package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.service.InstrumentService;
import com.tradingcapture.enrichment.domain.service.InstrumentService.BatchResolution;
import com.tradingcapture.enrichment.domain.service.InstrumentService.LookupKey;
import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalInstrumentController.class)
class InternalInstrumentControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentService instrumentService;

    private static final String BODY = """
            { "items": [ { "source": "EXCHANGE_SYMBOL", "value": "AAPL", "exchangeMic": "XNAS" } ] }
            """;

    @Test
    void machineTokenResolvesBatch() throws Exception {
        when(instrumentService.resolveBatch(anyList())).thenReturn(List.of(new BatchResolution(
                new LookupKey(IdentifierSource.EXCHANGE_SYMBOL, "AAPL", "XNAS"), true, false, null)));

        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .with(auth(EnrichmentTestTokens.allocationMachineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].found").value(true))
                .andExpect(jsonPath("$.results[0].source").value("EXCHANGE_SYMBOL"));
    }

    @Test
    void tokenWithoutServiceScopeIsForbidden() throws Exception {
        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .with(auth(EnrichmentTestTokens.scopedJwt("enrichment:admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyBatchIs400() throws Exception {
        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .with(auth(EnrichmentTestTokens.allocationMachineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest());
    }
}
