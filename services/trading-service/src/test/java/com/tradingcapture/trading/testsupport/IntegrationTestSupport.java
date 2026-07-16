package com.tradingcapture.trading.testsupport;

import com.jayway.jsonpath.JsonPath;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for full-service integration tests: real MySQL (singleton container), real Flyway
 * schema (V1 only — tests own their data), real security filter chain validating
 * test-issued RS256 tokens. Tables are wiped before each test for deterministic state.
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

    @BeforeEach
    void cleanTables() {
        // executions self-reference via root_execution_id/exec_ref_id, so a plain DELETE
        // trips the FK on parent rows; suspend FK checks for this one cleanup connection.
        jdbcTemplate.execute((java.sql.Connection connection) -> {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                statement.execute("DELETE FROM execution_misc_fees");
                statement.execute("DELETE FROM executions");
                statement.execute("DELETE FROM orders");
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    protected static String orderBody(String instrumentId, String quantity) {
        return """
                {
                  "instrumentId": "%s",
                  "accountId": "ACC-IT-1",
                  "side": "BUY",
                  "orderType": "LIMIT",
                  "price": "10.00",
                  "priceType": "PER_UNIT",
                  "currency": "USD",
                  "quantity": "%s",
                  "settleType": "T_PLUS_2",
                  "settleDate": "2026-07-20"
                }
                """.formatted(instrumentId, quantity);
    }

    /** Creates an order as the given identity and returns its orderId. */
    protected String createOrder(String bearer, String instrumentId, String quantity) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(instrumentId, quantity)))
                .andExpect(status().isCreated());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderId");
    }

    /** Captures a trade as admin and returns the new execution's internalId. */
    protected String captureTrade(String bearer, String orderId, String lastQty, String lastPx) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s", "lastQty": "%s", "lastPx": "%s" }
                                """.formatted(orderId, lastQty, lastPx)))
                .andExpect(status().isCreated());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.internalId");
    }
}
