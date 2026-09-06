package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.testsupport.RedisTestContainer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The Redis dedup pre-filter (ADR-0007): miss/hit/expiry against a real Redis container, and
 * fail-open behavior on an outage — the database remains authoritative, so a hit is only a hint and
 * a Redis failure never blocks or discards work.
 */
@Tag("integration")
class AllocationDedupFilterTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    private final OrchestrationMetrics metrics = new OrchestrationMetrics(new SimpleMeterRegistry());

    @BeforeAll
    static void redis() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(RedisTestContainer.host(), RedisTestContainer.port()));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    private AllocationDedupFilter filter(Duration ttl) {
        return new AllocationDedupFilter(redis, metrics, ttl);
    }

    @Test
    void unknownKeyIsAMiss() {
        assertThat(filter(Duration.ofMinutes(5)).isLikelyProcessed(key())).isFalse();
    }

    @Test
    void markedKeyIsAHit() {
        AllocationDedupFilter filter = filter(Duration.ofMinutes(5));
        String key = key();
        filter.markProcessed(key);
        assertThat(filter.isLikelyProcessed(key)).isTrue();
    }

    @Test
    void keyExpiresAfterTtl() {
        AllocationDedupFilter filter = filter(Duration.ofMillis(250));
        String key = key();
        filter.markProcessed(key);
        assertThat(filter.isLikelyProcessed(key)).isTrue();
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> !filter.isLikelyProcessed(key));
    }

    @Test
    void redisOutageFailsOpenSoTheDatabaseStaysAuthoritative() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6399),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        AllocationDedupFilter filter = new AllocationDedupFilter(deadTemplate, metrics, Duration.ofMinutes(5));

        try {
            assertThat(filter.isLikelyProcessed(key())).isFalse();          // miss, not an error to the caller
            assertThatNoException().isThrownBy(() -> filter.markProcessed(key())); // best effort
        } finally {
            dead.destroy();
        }
    }

    private static String key() {
        return "ALLOC-" + UUID.randomUUID();
    }
}
