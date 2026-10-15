package com.tradingcapture.account;

import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real resource-server validation path against test-issued RS256 tokens: signature,
 * issuer, audience, and expiry are all checked, and scope shortfalls become a 403.
 */
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    void missingTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.expiredAdminBearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.bearerWithoutAudience()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenWithoutTheReadScopeIs403() throws Exception {
        // account:admin alone cannot read accounts (that needs account:balance).
        String adminScopeOnly = TestTokenIssuer.issue(claims -> claims
                .claim("preferred_username", "tcs-admin")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .claim("scope", "account:admin")
                .audience(List.of(AccountTestTokens.AUDIENCE)));

        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminScopeOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void validTokenReadsSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.investorBearer("investor1")))
                .andExpect(status().isOk());
    }

    @Test
    void anInvestorIsRefusedAdminMutationsBeforeTheBodyIsValidated() throws Exception {
        String investor = "Bearer " + AccountTestTokens.investorBearer("investor1");
        for (var request : List.of(
                post("/api/v1/accounts/ACC-ANY/cash-transactions"),
                post("/api/v1/accounts/ACC-ANY/security-transactions"),
                put("/api/v1/accounts/ACC-ANY"))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, investor)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
    }

    @Test
    void anAdminSendingAnInvalidBodyStillGets400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-ANY/cash-transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
