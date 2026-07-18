package com.tradingcapture.trading.security;

import com.jayway.jsonpath.JsonPath;
import com.tradingcapture.testsupport.KeycloakTestContainer;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live Keycloak authorization matrix (Phase 9): a real Keycloak container imports the committed
 * {@code tcs} realm and issues <em>real</em> tokens, and the service runs its <em>production</em>
 * OIDC decoder (JWKS + issuer + audience) against that realm — no test key, no mocked security.
 * This proves the realm's role/scope/audience mappings actually produce the authorities the
 * authorization rules rely on.
 *
 * <p>Tokens: {@code investor1}/{@code investor2} (realm role INVESTOR), {@code tcs-admin} (ADMIN)
 * via the dev-only direct-grant client; the {@code allocation-engine} service account
 * (client-credentials, scope {@code allocation:process}); the {@code account-service} service
 * account (no trading audience) for the wrong-audience case.</p>
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        // jwt-mode defaults to oidc → the production SupplierJwtDecoder is used; the issuer is
        // pointed at the live container below so JWKS discovery and issuer validation are real.
        "tcs.security.jwt-mode=oidc"
})
@AutoConfigureMockMvc
@Tag("integration")
class LiveKeycloakAuthorizationTest extends MySqlContainerSupport {

    @DynamicPropertySource
    static void oidcIssuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                KeycloakTestContainer::issuerUri);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
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

    private static String investor1() {
        return KeycloakTestContainer.userToken("investor1", "investor1-dev");
    }

    private static String investor2() {
        return KeycloakTestContainer.userToken("investor2", "investor2-dev");
    }

    private static String admin() {
        return KeycloakTestContainer.userToken("tcs-admin", "tcs-admin-dev");
    }

    private static String allocationEngine() {
        return KeycloakTestContainer.clientCredentialsToken("allocation-engine", "allocation-engine-dev-secret");
    }

    private static String wrongAudience() {
        // trading-service's own service account carries account-service + enrichment-service audiences
        // (never trading-service) — a valid, signed token bearing OTHER services' audiences, so this
        // proves audience *isolation* (a different-but-present aud is rejected), not merely a missing aud.
        return KeycloakTestContainer.clientCredentialsToken("trading-service", "trading-service-dev-secret");
    }

    private static String orderBody() {
        return """
                {
                  "instrumentId": "AAPL", "accountId": "ACC-LIVE-1", "side": "BUY",
                  "orderType": "LIMIT", "price": "10.00", "priceType": "PER_UNIT",
                  "currency": "USD", "quantity": "100", "settleType": "T_PLUS_2",
                  "settleDate": "2026-07-20"
                }
                """;
    }

    private String createOrderAs(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.orderId");
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void realInvestorTokenCreatesAndReadsOwnOrder() throws Exception {
        String orderId = createOrderAs(investor1());
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderId").value(orderId));
    }

    @Test
    void investorCannotSeeAnotherInvestorsOrderIsMaskedAsNotFound() throws Exception {
        String orderId = createOrderAs(investor1());
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor2()))
                .andExpect(status().isNotFound());
    }

    @Test
    void investorLacksAdminRoleForExecutionWrite() throws Exception {
        // Valid audience + execution:write scope, but INVESTOR (not ADMIN) → 403, not 401.
        String orderId = createOrderAs(investor1());
        mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"orderId\": \"" + orderId + "\", \"lastQty\": \"1\", \"lastPx\": \"10.00\" }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void realAdminTokenCapturesExecution() throws Exception {
        String orderId = createOrderAs(admin());
        mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"orderId\": \"" + orderId + "\", \"lastQty\": \"1\", \"lastPx\": \"10.00\" }"))
                .andExpect(status().isCreated());
    }

    @Test
    void wrongAudienceTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void machineServiceAccountResolvesExecutions() throws Exception {
        // allocation:process + trading-service audience → reaches the machine endpoint (unknown
        // ExecIDs resolve to NOT_FOUND verdicts, a 200 body — the point is authorization passes).
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + allocationEngine())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"execIds\": [\"EXEC-UNKNOWN-1\"] }"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenCannotReachMachineEndpoint() throws Exception {
        // investor has the trading-service audience but not allocation:process → 403 at /internal.
        mockMvc.perform(post("/internal/v1/executions/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"execIds\": [\"EXEC-UNKNOWN-1\"] }"))
                .andExpect(status().isForbidden());
    }
}
