package com.tradingcapture.allocation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the outbox poller's fixed-delay schedule (ADR-0005). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
