package com.tradingcapture.enrichment.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Read-through cache over Redis for reference-data lookups (ADR-0003, ADR-0007). Redis is a
 * non-authoritative optimization: every operation degrades gracefully to the database. A Redis
 * outage or a serialization error is logged and counted, then treated as a miss (reads) or a
 * no-op (writes/evictions) — it never propagates, so correctness never depends on Redis.
 *
 * <p>Values are stored as JSON of the API response DTOs with a configurable TTL.</p>
 */
@Component
public class ReferenceDataCache {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final EnrichmentMetrics metrics;
    private final Duration ttl;

    public ReferenceDataCache(StringRedisTemplate redis,
                              ObjectMapper mapper,
                              EnrichmentMetrics metrics,
                              @Value("${tcs.cache.ttl}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.metrics = metrics;
        this.ttl = ttl;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                metrics.cacheMiss();
                return Optional.empty();
            }
            metrics.cacheHit();
            return Optional.of(mapper.readValue(json, type));
        } catch (RuntimeException | IOException e) {
            degraded("get", key, e);
            return Optional.empty();
        }
    }

    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                metrics.cacheMiss();
                return Optional.empty();
            }
            metrics.cacheHit();
            JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return Optional.of(mapper.readValue(json, listType));
        } catch (RuntimeException | IOException e) {
            degraded("getList", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (RuntimeException | IOException e) {
            degraded("put", key, e);
        }
    }

    public void evict(String... keys) {
        try {
            redis.delete(Arrays.asList(keys));
            metrics.cacheEviction();
        } catch (RuntimeException e) {
            degraded("evict", String.join(",", keys), e);
        }
    }

    private void degraded(String op, String key, Exception e) {
        metrics.cacheError();
        // Not silent: the DB remains authoritative, but we record and log the degradation.
        log.warn("Redis {} failed for key '{}' — degrading to the database: {}", op, key, e.toString());
    }
}
