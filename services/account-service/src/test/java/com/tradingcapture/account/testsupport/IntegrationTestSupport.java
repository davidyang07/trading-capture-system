package com.tradingcapture.account.testsupport;

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
 * schema (V1 only — tests own their data), real security filter chain validating test-issued
 * RS256 tokens. Tables are wiped before each test for deterministic state.
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
        jdbcTemplate.update("DELETE FROM cash_transactions");
        jdbcTemplate.update("DELETE FROM security_transactions");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    protected static String createBody(String name, String type, String currency) {
        return """
                { "accountName": "%s", "accountType": "%s", "baseCurrency": "%s" }
                """.formatted(name, type, currency);
    }

    /** Creates an account as the given identity and returns its accountId. */
    protected String createAccount(String bearer, String name, String currency) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, "CASH", currency)))
                .andExpect(status().isCreated());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accountId");
    }

    protected void transition(String bearer, String accountId, String action) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/" + action)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isOk());
    }

    protected ResultActions postCash(String bearer, String accountId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/" + accountId + "/cash-transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions postSecurity(String bearer, String accountId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/" + accountId + "/security-transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
