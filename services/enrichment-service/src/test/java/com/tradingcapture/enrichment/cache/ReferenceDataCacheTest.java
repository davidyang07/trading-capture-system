package com.tradingcapture.enrichment.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The cache serves hits, records misses, and — critically — degrades to the DB when Redis fails. */
@ExtendWith(MockitoExtension.class)
class ReferenceDataCacheTest {

    private record Sample(String a, int b) {
    }

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private EnrichmentMetrics metrics;

    private final ObjectMapper mapper = new ObjectMapper();
    private ReferenceDataCache cache;

    @BeforeEach
    void setUp() {
        cache = new ReferenceDataCache(redis, mapper, metrics, Duration.ofMinutes(15));
    }

    @Test
    void hitDeserializesAndCounts() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("k")).thenReturn(mapper.writeValueAsString(new Sample("x", 1)));

        Optional<Sample> result = cache.get("k", Sample.class);

        assertThat(result).contains(new Sample("x", 1));
        verify(metrics).cacheHit();
    }

    @Test
    void missReturnsEmptyAndCounts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("k")).thenReturn(null);

        assertThat(cache.get("k", Sample.class)).isEmpty();
        verify(metrics).cacheMiss();
    }

    @Test
    void redisOutageDegradesToAMissAndCountsAnError() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("k")).thenThrow(new RedisConnectionFailureException("redis down"));

        // No exception propagates — the caller will fall back to the database.
        assertThat(cache.get("k", Sample.class)).isEmpty();
        verify(metrics).cacheError();
    }

    @Test
    void putWritesWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        cache.put("k", new Sample("y", 2));

        verify(valueOps).set(eq("k"), anyString(), any(Duration.class));
    }

    @Test
    void putSurvivesARedisOutage() {
        when(redis.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        cache.put("k", new Sample("y", 2)); // must not throw
        verify(metrics).cacheError();
    }

    @Test
    void evictDeletesKeysAndCounts() {
        cache.evict("k1", "k2");

        verify(redis).delete(List.of("k1", "k2"));
        verify(metrics).cacheEviction();
    }

    @Test
    void evictInsideATransactionDeletesAgainAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict("k1");
            verify(redis, org.mockito.Mockito.times(1)).delete(List.of("k1"));

            // A reader may have re-cached the old row before the commit; the commit evicts it again.
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(redis, org.mockito.Mockito.times(2)).delete(List.of("k1"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
