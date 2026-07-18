package com.tradingcapture.allocation.orchestration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis deduplication pre-filter (ADR-0007) — a fast path in front of the database-authoritative
 * idempotency (the unique {@code (alloc_id, alloc_trans_type)} key and per-allocation state). A hit
 * means "likely already handled — confirm against the database before skipping work"; it never
 * blindly discards work, so a false positive cannot drop a valid allocation. On any Redis failure
 * the filter fails open (treated as a miss / best-effort write) and the database remains the sole
 * authority. Keys are an explicit namespace over the business key only — never a secret or payload.
 */
@Component
public class AllocationDedupFilter {

    private static final String KEY_PREFIX = "tcs:alloc:dedup:";

    private final StringRedisTemplate redis;
    private final OrchestrationMetrics metrics;
    private final Duration ttl;

    public AllocationDedupFilter(StringRedisTemplate redis, OrchestrationMetrics metrics,
                                 @Value("${tcs.orchestration.dedup-ttl:PT30M}") Duration ttl) {
        this.redis = redis;
        this.metrics = metrics;
        this.ttl = ttl;
    }

    /** True if the business key is marked processed in Redis (caller must still confirm via the DB). */
    public boolean isLikelyProcessed(String businessKey) {
        try {
            boolean hit = Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + businessKey));
            metrics.dedup(hit ? "hit" : "miss");
            return hit;
        } catch (RuntimeException e) {
            metrics.dedup("error");
            return false; // fail open — the database is authoritative
        }
    }

    /** Best-effort mark that the business key reached a terminal outcome. */
    public void markProcessed(String businessKey) {
        try {
            redis.opsForValue().set(KEY_PREFIX + businessKey, "1", ttl);
        } catch (RuntimeException e) {
            metrics.dedup("error"); // best effort; a lost write only costs a redundant DB check
        }
    }
}
