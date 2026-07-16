package com.tradingcapture.enrichment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Domain and cache counters (Micrometer). The cache meters make Redis's value measurable
 * (hit/miss/error ratios); exposure over Prometheus arrives in Phase 11.
 */
@Component
public class EnrichmentMetrics {

    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheErrors;
    private final Counter cacheEvictions;
    private final Counter referenceMutations;

    public EnrichmentMetrics(MeterRegistry registry) {
        this.cacheHits = Counter.builder("tcs.enrichment.cache.hit")
                .description("Reference lookups served from Redis").register(registry);
        this.cacheMisses = Counter.builder("tcs.enrichment.cache.miss")
                .description("Reference lookups that fell through to the database").register(registry);
        this.cacheErrors = Counter.builder("tcs.enrichment.cache.error")
                .description("Redis operations that failed and degraded to the database").register(registry);
        this.cacheEvictions = Counter.builder("tcs.enrichment.cache.eviction")
                .description("Cache keys invalidated on an administrative mutation").register(registry);
        this.referenceMutations = Counter.builder("tcs.enrichment.reference.mutations")
                .description("Administrative reference-data create/update operations").register(registry);
    }

    public void cacheHit() {
        cacheHits.increment();
    }

    public void cacheMiss() {
        cacheMisses.increment();
    }

    public void cacheError() {
        cacheErrors.increment();
    }

    public void cacheEviction() {
        cacheEvictions.increment();
    }

    public void referenceMutation() {
        referenceMutations.increment();
    }
}
