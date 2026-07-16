package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The live springdoc document must cover every path of the contract of record. */
class OpenApiDocsIntegrationTest extends IntegrationTestSupport {

    @Test
    void liveApiDocsExposeEveryContractPath() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Trading Service API"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/executions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/executions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/executions/{internalId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/executions/{internalId}/correct'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/executions/{internalId}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/executions/allocation-state'].put").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
    }
}
