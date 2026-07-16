package com.tradingcapture.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Redis 7.4 Testcontainer shared by every integration test in a JVM. Like the MySQL
 * singleton it is started once and never explicitly stopped (Ryuk reaps it at JVM exit). The
 * image matches the docker-compose Redis version so cache behavior is representative.
 *
 * <p>Redis is not a base class because a test typically also needs the MySQL base; instead a
 * test's {@code @DynamicPropertySource} points {@code spring.data.redis.*} at {@link #host()} /
 * {@link #port()}.</p>
 */
public final class RedisTestContainer {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private RedisTestContainer() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }
}
