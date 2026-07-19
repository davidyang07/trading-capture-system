package com.tradingcapture.enrichment;

import com.tradingcapture.testsupport.KeycloakTestContainer;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.RedisTestContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live Keycloak authorization matrix for enrichment-service (Phase 9): the production OIDC decoder
 * validates <em>real</em> realm-issued tokens (audience {@code enrichment-service}). Reference-data
 * reads need {@code enrichment:read}; mutations additionally need the {@code ADMIN} realm role; the
 * machine lookup accepts {@code enrichment:read} or {@code allocation:process} (reference data is
 * non-owner global data — an investor read token is intentionally allowed on {@code /internal}).
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "tcs.security.jwt-mode=oidc"
})
@AutoConfigureMockMvc
@Tag("integration")
class LiveKeycloakAuthorizationTest extends MySqlContainerSupport {

    @DynamicPropertySource
    static void liveProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                KeycloakTestContainer::issuerUri);
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @Autowired
    private MockMvc mockMvc;

    private static String investor1() {
        return KeycloakTestContainer.userToken("investor1", "investor1-dev");
    }

    private static String allocationEngine() {
        return KeycloakTestContainer.clientCredentialsToken("allocation-engine", "allocation-engine-dev-secret");
    }

    private static String wrongAudience() {
        // account-service service account carries no enrichment-service audience.
        return KeycloakTestContainer.clientCredentialsToken("account-service", "account-service-dev-secret");
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void anonymousMachineLookupIsUnauthorized() throws Exception {
        // Guards the /internal lookup against an accidental permitAll: no token must never pass,
        // even though the path also accepts a user enrichment:read token by design.
        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [ { \"source\": \"CUSIP\", \"value\": \"UNKNOWN123\" } ] }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void investorCanReadReferenceData() throws Exception {
        mockMvc.perform(get("/api/v1/instruments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1()))
                .andExpect(status().isOk());
    }

    @Test
    void investorCannotMutateReferenceDataWithoutAdminRole() throws Exception {
        // Investor holds enrichment:admin scope but not the ADMIN realm role → 403 (no body needed).
        mockMvc.perform(post("/api/v1/instruments/INSTR-UNKNOWN/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor1()))
                .andExpect(status().isForbidden());
    }

    @Test
    void machineServiceAccountLooksUpInstruments() throws Exception {
        mockMvc.perform(post("/internal/v1/instruments/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + allocationEngine())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [ { \"source\": \"CUSIP\", \"value\": \"UNKNOWN123\" } ] }"))
                .andExpect(status().isOk());
    }

    @Test
    void wrongAudienceTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/instruments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience()))
                .andExpect(status().isUnauthorized());
    }
}
