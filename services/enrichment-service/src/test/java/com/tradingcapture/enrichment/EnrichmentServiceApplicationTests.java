package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot smoke test against real MySQL and Redis (singleton containers) with Flyway applied:
 * proves the context wires (JPA, Redis, security, observability, springdoc) and that the public
 * actuator health endpoint answers with a correlation header.
 */
class EnrichmentServiceApplicationTests extends IntegrationTestSupport {

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")));
    }

    @Test
    void responsesCarryCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Correlation-ID"));
    }
}
