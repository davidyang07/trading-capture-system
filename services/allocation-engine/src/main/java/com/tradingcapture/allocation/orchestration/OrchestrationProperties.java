package com.tradingcapture.allocation.orchestration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Orchestration / recovery-driver knobs ({@code tcs.orchestration.*}): scheduling, batch size,
 * claim lease timeout (after which an abandoned claim is reclaimable), retry backoff, and an
 * {@code enabled} flag so tests can run without a competing scheduled driver.
 */
@ConfigurationProperties("tcs.orchestration")
public class OrchestrationProperties {

    private boolean enabled = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private int batchSize = 20;
    private Duration claimLease = Duration.ofMinutes(2);
    private Duration retryBackoffBase = Duration.ofSeconds(5);
    private double retryBackoffMultiplier = 2.0;
    private Duration retryBackoffMax = Duration.ofMinutes(10);
    private int maxStepsPerRun = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getClaimLease() {
        return claimLease;
    }

    public void setClaimLease(Duration claimLease) {
        this.claimLease = claimLease;
    }

    public Duration getRetryBackoffBase() {
        return retryBackoffBase;
    }

    public void setRetryBackoffBase(Duration retryBackoffBase) {
        this.retryBackoffBase = retryBackoffBase;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public Duration getRetryBackoffMax() {
        return retryBackoffMax;
    }

    public void setRetryBackoffMax(Duration retryBackoffMax) {
        this.retryBackoffMax = retryBackoffMax;
    }

    public int getMaxStepsPerRun() {
        return maxStepsPerRun;
    }

    public void setMaxStepsPerRun(int maxStepsPerRun) {
        this.maxStepsPerRun = maxStepsPerRun;
    }
}
