package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Machine batch instrument resolution over real MySQL: independent found/ambiguous/not-found verdicts. */
class BatchLookupIntegrationTest extends IntegrationTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        seedReferenceData();
        createInstrument("""
                { "instrumentId": "AAPL", "name": "Apple", "instrumentType": "EQUITY", "exchangeMic": "XNAS",
                  "currencyCode": "USD", "identifiers": [ { "source": "EXCHANGE_SYMBOL", "value": "AAPL" } ] }
                """).andExpect(status().isCreated());
        createInstrument("""
                { "instrumentId": "XYZ.OQ", "name": "XYZ Nasdaq", "instrumentType": "EQUITY", "exchangeMic": "XNAS",
                  "currencyCode": "USD", "identifiers": [ { "source": "EXCHANGE_SYMBOL", "value": "XYZ" } ] }
                """).andExpect(status().isCreated());
        createInstrument("""
                { "instrumentId": "XYZ.L", "name": "XYZ London", "instrumentType": "EQUITY", "exchangeMic": "XLON",
                  "currencyCode": "GBP", "identifiers": [ { "source": "EXCHANGE_SYMBOL", "value": "XYZ" } ] }
                """).andExpect(status().isCreated());
    }

    @Test
    void batchYieldsIndependentVerdicts() throws Exception {
        String body = """
                { "items": [
                    { "source": "EXCHANGE_SYMBOL", "value": "AAPL", "exchangeMic": "XNAS" },
                    { "source": "EXCHANGE_SYMBOL", "value": "XYZ" },
                    { "source": "ISIN", "value": "US0000000000" }
                ] }
                """;

        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.allocationMachineBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].found").value(true))
                .andExpect(jsonPath("$.results[0].instrument.instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.results[1].found").value(true))
                .andExpect(jsonPath("$.results[1].ambiguous").value(true))
                .andExpect(jsonPath("$.results[1].instrument").doesNotExist())
                .andExpect(jsonPath("$.results[2].found").value(false));
    }
}
