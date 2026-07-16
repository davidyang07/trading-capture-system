package com.tradingcapture.trading;

import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token validation through the real decoder path (signature, expiry, audience) plus the
 * standardized 401/403 problem contract and the documented public paths.
 */
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    void anonymousRequestGets401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void garbageTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void tokenWithoutRequiredAudienceGets401() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TradingTestTokens.bearerWithoutAudience()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TradingTestTokens.expiredAdminBearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientRoleGets403ProblemThroughTheFullStack() throws Exception {
        mockMvc.perform(post("/api/v1/executions")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TradingTestTokens.investorBearer("investor1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "ORD-X", "lastQty": "1", "lastPx": "1.00" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void validTokenPassesEndToEnd() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TradingTestTokens.adminBearer()))
                .andExpect(status().isOk());
    }

    @Test
    void documentedPublicPathsNeedNoToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void actuatorBeyondHealthAndInfoIsNotExposed() throws Exception {
        // anonymous: the security chain rejects anything beyond the public paths first
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        // authenticated: the endpoint is genuinely not exposed
        String admin = TradingTestTokens.adminBearer();
        mockMvc.perform(get("/actuator/env")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}
