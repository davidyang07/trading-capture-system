package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The live springdoc contract is served publicly and documents the enrichment paths. */
class OpenApiDocsIntegrationTest extends IntegrationTestSupport {

    @Test
    void apiDocsAreServedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Enrichment Service API"))
                .andExpect(jsonPath("$.paths['/api/v1/instruments']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/instruments/by-identifier']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/instruments/lookup']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/fee-rules/applicable']").exists());
    }
}
