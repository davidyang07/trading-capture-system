package com.tradingcapture.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton MySQL 8.4 Testcontainer shared by every integration test class in a JVM.
 *
 * <p>The container is started once in a static initializer and deliberately never stopped:
 * Testcontainers' Ryuk reaper removes it when the JVM exits. Sharing one container keeps
 * integration suites fast and avoids per-class startup flakiness. The image matches the
 * docker-compose MySQL version so migration behavior is representative.</p>
 *
 * <p>Subclasses receive {@code spring.datasource.*} pointing at the container. Flyway runs
 * against the container's default database; migrations must therefore never hardcode a
 * schema name (they don't — the deployed schema name comes from the JDBC URL).</p>
 */
public abstract class MySqlContainerSupport {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withUrlParam("connectionTimeZone", "UTC")
                    .withDatabaseName("tcs_trading_test");

    static {
        MYSQL.start();
    }

    protected static MySQLContainer<?> mysql() {
        return MYSQL;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
