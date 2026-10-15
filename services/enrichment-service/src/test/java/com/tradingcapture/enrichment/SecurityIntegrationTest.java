package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real resource-server validation: signature/issuer/audience/expiry and scope enforcement. */
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    void missingTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/currencies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.expiredReaderBearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        mockMvc.perform(get("/api/v1/currencies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.bearerWithoutAudience()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readScopeCannotMutate() throws Exception {
        mockMvc.perform(post("/api/v1/currencies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.readerBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"code\": \"EUR\", \"name\": \"Euro\", \"minorUnits\": 2 }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void readScopeCanRead() throws Exception {
        mockMvc.perform(get("/api/v1/currencies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.readerBearer()))
                .andExpect(status().isOk());
    }

    @Test
    void readScopeIsRefusedMutationsBeforeTheBodyIsValidated() throws Exception {
        String reader = "Bearer " + EnrichmentTestTokens.readerBearer();
        for (var request : List.of(post("/api/v1/instruments"), post("/api/v1/fee-rules"),
                put("/api/v1/currencies/EUR"), post("/api/v1/settlement-options"))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, reader)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
    }

    @Test
    void anAdminSendingAnInvalidBodyStillGets400() throws Exception {
        mockMvc.perform(post("/api/v1/currencies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
