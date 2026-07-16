package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end instrument administration and identifier lookups (including ambiguity) over real MySQL/Redis. */
class InstrumentIntegrationTest extends IntegrationTestSupport {

    @BeforeEach
    void setUpReferences() throws Exception {
        seedReferenceData();
    }

    private String reader() {
        return EnrichmentTestTokens.readerBearer();
    }

    private static String instrumentBody(String id, String name, String exchange, String currency, String identifiers) {
        return """
                { "instrumentId": "%s", "name": "%s", "instrumentType": "EQUITY",
                  "exchangeMic": "%s", "currencyCode": "%s", "identifiers": [ %s ] }
                """.formatted(id, name, exchange, currency, identifiers);
    }

    @Test
    void createThenFetchIncludesIdentifiers() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD",
                "{ \"source\": \"ISIN\", \"value\": \"US0378331005\" }, { \"source\": \"EXCHANGE_SYMBOL\", \"value\": \"AAPL\" }"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/instruments/AAPL").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.alternateIdentifiers.length()").value(2));
    }

    @Test
    void duplicateInstrumentIdIs409() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD", "")).andExpect(status().isCreated());
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_INSTRUMENT_ID"));
    }

    @Test
    void unknownExchangeIs400() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XPAR", "USD", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFERENCE"));
    }

    @Test
    void lookupByGlobalIdentifierResolves() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD",
                "{ \"source\": \"ISIN\", \"value\": \"US0378331005\" }")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/instruments/by-identifier")
                        .param("source", "ISIN").param("value", "us0378331005") // lower-case tolerated
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("AAPL"));
    }

    @Test
    void ambiguousExchangeSymbolNeedsAnExchange() throws Exception {
        createInstrument(instrumentBody("XYZ.OQ", "XYZ Nasdaq", "XNAS", "USD",
                "{ \"source\": \"EXCHANGE_SYMBOL\", \"value\": \"XYZ\" }")).andExpect(status().isCreated());
        createInstrument(instrumentBody("XYZ.L", "XYZ London", "XLON", "GBP",
                "{ \"source\": \"EXCHANGE_SYMBOL\", \"value\": \"XYZ\" }")).andExpect(status().isCreated());

        // Without an exchange: ambiguous.
        mockMvc.perform(get("/api/v1/instruments/by-identifier")
                        .param("source", "EXCHANGE_SYMBOL").param("value", "XYZ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AMBIGUOUS_IDENTIFIER"));

        // With the exchange: resolved.
        mockMvc.perform(get("/api/v1/instruments/by-identifier")
                        .param("source", "EXCHANGE_SYMBOL").param("value", "XYZ").param("exchange", "XNAS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("XYZ.OQ"));
    }

    @Test
    void reusingAGlobalIsinAcrossInstrumentsIs409() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD",
                "{ \"source\": \"ISIN\", \"value\": \"US0378331005\" }")).andExpect(status().isCreated());

        createInstrument(instrumentBody("FAKE", "Fake", "XNAS", "USD",
                "{ \"source\": \"ISIN\", \"value\": \"US0378331005\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_IDENTIFIER"));
    }

    @Test
    void updateReplacesIdentifiersAndDeactivateWorks() throws Exception {
        createInstrument(instrumentBody("AAPL", "Apple", "XNAS", "USD",
                "{ \"source\": \"ISIN\", \"value\": \"US0378331005\" }")).andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/instruments/AAPL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Apple Inc", "instrumentType": "EQUITY", "exchangeMic": "XNAS",
                                  "currencyCode": "USD",
                                  "identifiers": [ { "source": "CUSIP", "value": "037833100" } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternateIdentifiers.length()").value(1))
                .andExpect(jsonPath("$.alternateIdentifiers[0].source").value("CUSIP"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/instruments/AAPL/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}
