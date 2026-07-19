package com.tradingcapture.account;

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
 * Live Keycloak authorization matrix for account-service (Phase 9): the production OIDC decoder
 * validates <em>real</em> realm-issued tokens (JWKS + issuer + audience {@code account-service}),
 * proving the realm mappings, ownership 404-masking, machine-scope separation, and audience
 * isolation actually hold end-to-end — not just against a hand-built token.
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
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
        jdbcTemplate.update("DELETE FROM cash_transactions");
        jdbcTemplate.update("DELETE FROM security_transactions");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    private static String investor1() {
        return KeycloakTestContainer.userToken("investor1", "investor1-dev");
    }

    private static String investor2() {
        return KeycloakTestContainer.userToken("investor2", "investor2-dev");
    }

    private static String allocationEngine() {
        return KeycloakTestContainer.clientCredentialsToken("allocation-engine", "allocation-engine-dev-secret");
    }

    private static String wrongAudience() {
        // account-service service account carries no account-service audience.
        return KeycloakTestContainer.clientCredentialsToken("account-service", "account-service-dev-secret");
    }

    private String createAccountAs(String bearer) throws Exception {
        String json = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"accountName\": \"Live Test\", \"accountType\": \"CASH\", \"baseCurrency\": \"USD\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accountId");
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void investorCreatesAndReadsOwnAccount() throws Exception {
        String accountId = createAccountAs(investor1());
        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1()))
                .andExpect(status().isOk());
    }

    @Test
    void investorCannotSeeAnotherInvestorsAccountIsMaskedAsNotFound() throws Exception {
        String accountId = createAccountAs(investor1());
        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor2()))
                .andExpect(status().isNotFound());
    }

    @Test
    void machineServiceAccountValidatesBatch() throws Exception {
        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + allocationEngine())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [ { \"accountId\": \"ACC-UNKNOWN-1\" } ] }"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenCannotReachMachineBatch() throws Exception {
        // Investor holds account:balance but not account:validate/allocation:process → 403.
        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [ { \"accountId\": \"ACC-UNKNOWN-1\" } ] }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongAudienceTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience()))
                .andExpect(status().isUnauthorized());
    }
}
