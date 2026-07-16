package com.tradingcapture.enrichment.testsupport;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.RedisTestContainer;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for full-service integration tests: real MySQL and real Redis (singleton containers),
 * real Flyway schema (V1 only — tests own their data), real security filter chain validating
 * test-issued RS256 tokens. Tables and the Redis keyspace are cleared before each test.
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "tcs.security.jwt-mode=local-keys",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + TestTokenIssuer.ISSUER
})
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Tag("integration")
public abstract class IntegrationTestSupport extends MySqlContainerSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM instrument_alt_ids");
        jdbcTemplate.update("DELETE FROM fee_rules");
        jdbcTemplate.update("DELETE FROM instruments");
        jdbcTemplate.update("DELETE FROM exchanges");
        jdbcTemplate.update("DELETE FROM currencies");
        jdbcTemplate.update("DELETE FROM settlement_options");
        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }

    // ------------------------------------------------------------------ API helpers

    protected ResultActions adminPost(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Creates the XNAS/XLON exchanges and USD/GBP currencies most instrument tests need. */
    protected void seedReferenceData() throws Exception {
        adminPost("/api/v1/exchanges", """
                { "mic": "XNAS", "name": "Nasdaq", "country": "US", "timezone": "America/New_York" }
                """).andExpect(status().isCreated());
        adminPost("/api/v1/exchanges", """
                { "mic": "XLON", "name": "LSE", "country": "GB", "timezone": "Europe/London" }
                """).andExpect(status().isCreated());
        adminPost("/api/v1/currencies", """
                { "code": "USD", "name": "US Dollar", "minorUnits": 2 }
                """).andExpect(status().isCreated());
        adminPost("/api/v1/currencies", """
                { "code": "GBP", "name": "Pound", "minorUnits": 2 }
                """).andExpect(status().isCreated());
    }

    protected ResultActions createInstrument(String body) throws Exception {
        return adminPost("/api/v1/instruments", body);
    }
}
