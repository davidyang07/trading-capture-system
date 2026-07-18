package com.tradingcapture.allocation.config;

import com.tradingcapture.allocation.orchestration.OrchestrationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the outbox poller (ADR-0005) and the orchestration driver (ADR-0006) fixed-delay schedules. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrchestrationProperties.class)
public class SchedulingConfig {
}
