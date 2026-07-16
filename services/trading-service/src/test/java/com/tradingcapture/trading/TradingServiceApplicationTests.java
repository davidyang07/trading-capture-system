package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradingServiceApplicationTests extends IntegrationTestSupport {

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void responsesCarryCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Correlation-ID"));
    }
}
