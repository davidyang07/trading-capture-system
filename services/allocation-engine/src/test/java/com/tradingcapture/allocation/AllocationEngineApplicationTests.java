package com.tradingcapture.allocation;

import com.tradingcapture.allocation.testsupport.AllocationIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot smoke against real MySQL + Kafka (singleton containers) with Flyway applied: proves
 * the full context wires (JPA, Kafka listener + retry topology, security, scheduling,
 * observability) and the public actuator health endpoint answers with a correlation header.
 * Isolated topic/group so this cached context never consumes another test's records; outbox
 * poller off so the pipeline context stays the single publisher.
 */
@SpringBootTest(properties = {
        "tcs.ingest.inbound-topic=tcs.fix.allocation-instruction.smoke-it.v1",
        "spring.kafka.consumer.group-id=allocation-engine-smoke-it",
        "tcs.outbox.enabled=false"
})
@AutoConfigureMockMvc
class AllocationEngineApplicationTests extends AllocationIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void responsesCarryCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void nonPublicPathsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theOutboxPollerAndOrchestrationDriverDoNotShareOneSchedulerThread() {
        assertThat(taskScheduler.getPoolSize()).isGreaterThanOrEqualTo(2);
    }
}
