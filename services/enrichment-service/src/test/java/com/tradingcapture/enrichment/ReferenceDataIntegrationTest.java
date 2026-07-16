package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exchanges, settlement options, and fee rules end to end, including applicable-rule filtering. */
class ReferenceDataIntegrationTest extends IntegrationTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        seedReferenceData();
    }

    private String reader() {
        return EnrichmentTestTokens.readerBearer();
    }

    @Test
    void duplicateExchangeIs409() throws Exception {
        adminPost("/api/v1/exchanges", """
                { "mic": "XNAS", "name": "Nasdaq again", "country": "US", "timezone": "America/New_York" }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EXCHANGE"));
    }

    @Test
    void settlementOptionCreateAndGet() throws Exception {
        adminPost("/api/v1/settlement-options", """
                { "code": "T_PLUS_2", "name": "Regular way", "settlementDays": 2, "description": "Standard" }
                """).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/settlement-options/T_PLUS_2").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementDays").value(2));

        mockMvc.perform(get("/api/v1/settlement-options/UNKNOWN").header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SETTLEMENT_OPTION_NOT_FOUND"));
    }

    @Test
    void feeRulesApplicableFiltersByDimension() throws Exception {
        adminPost("/api/v1/fee-rules", """
                { "feeRuleId": "US-EQ", "name": "US eq commission", "feeType": "COMMISSION", "basis": "PERCENTAGE",
                  "rate": "0.00100000", "currencyCode": "USD", "instrumentType": "EQUITY" }
                """).andExpect(status().isCreated());
        adminPost("/api/v1/fee-rules", """
                { "feeRuleId": "LSE", "name": "LSE fee", "feeType": "EXCHANGE_FEE", "basis": "PER_UNIT",
                  "rate": "0.05000000", "currencyCode": "GBP", "exchangeMic": "XLON" }
                """).andExpect(status().isCreated());
        adminPost("/api/v1/fee-rules", """
                { "feeRuleId": "GLOBAL", "name": "Clearing", "feeType": "CLEARING_FEE", "basis": "ABSOLUTE",
                  "rate": "1.00000000", "currencyCode": "USD" }
                """).andExpect(status().isCreated());

        // US equity in USD → US-EQ + GLOBAL, not the GBP/XLON rule.
        mockMvc.perform(get("/api/v1/fee-rules/applicable")
                        .param("exchange", "XNAS").param("instrumentType", "EQUITY").param("currency", "USD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].feeRuleId", org.hamcrest.Matchers.containsInAnyOrder("GLOBAL", "US-EQ")));
    }

    @Test
    void unknownExchangeDimensionOnFeeRuleIs400() throws Exception {
        adminPost("/api/v1/fee-rules", """
                { "feeRuleId": "BAD", "name": "bad", "feeType": "COMMISSION", "basis": "PERCENTAGE",
                  "rate": "0.001", "exchangeMic": "XPAR" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFERENCE"));
    }
}
