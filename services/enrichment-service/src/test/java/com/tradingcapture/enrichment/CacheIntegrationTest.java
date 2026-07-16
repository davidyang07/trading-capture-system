package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis read-through behavior against real Redis: a lookup is cached (a subsequent direct DB
 * change is not visible until the key is invalidated), and an administrative mutation invalidates
 * the key so the next read is fresh.
 */
class CacheIntegrationTest extends IntegrationTestSupport {

    @BeforeEach
    void setUpReferences() throws Exception {
        seedReferenceData();
    }

    private String reader() {
        return EnrichmentTestTokens.readerBearer();
    }

    @Test
    void instrumentLookupIsCachedThenInvalidatedOnUpdate() throws Exception {
        createInstrument("""
                { "instrumentId": "AAPL", "name": "Apple", "instrumentType": "EQUITY",
                  "exchangeMic": "XNAS", "currencyCode": "USD", "identifiers": [] }
                """).andExpect(status().isCreated());

        // First read populates the cache.
        mockMvc.perform(get("/api/v1/instruments/AAPL").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("Apple"));

        // A direct DB change bypasses the cache; the cached value must still be served.
        jdbcTemplate.update("UPDATE instruments SET name = ? WHERE instrument_id = ?", "DB-ONLY", "AAPL");
        mockMvc.perform(get("/api/v1/instruments/AAPL").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("Apple")); // stale cache hit proves caching

        // An admin update invalidates the key; the next read reflects the update.
        mockMvc.perform(put("/api/v1/instruments/AAPL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Apple Inc", "instrumentType": "EQUITY", "exchangeMic": "XNAS",
                                  "currencyCode": "USD", "identifiers": [] }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/instruments/AAPL").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("Apple Inc"));
    }

    @Test
    void currencyLookupIsCachedThenInvalidatedOnUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/currencies/USD").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("US Dollar"));

        jdbcTemplate.update("UPDATE currencies SET name = ? WHERE code = ?", "DB-ONLY", "USD");
        mockMvc.perform(get("/api/v1/currencies/USD").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("US Dollar")); // cache hit

        mockMvc.perform(put("/api/v1/currencies/USD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"United States Dollar\", \"minorUnits\": 2 }"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/currencies/USD").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(jsonPath("$.name").value("United States Dollar"));
    }
}
