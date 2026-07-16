package com.tradingcapture.enrichment;

import com.tradingcapture.enrichment.testsupport.EnrichmentTestTokens;
import com.tradingcapture.enrichment.testsupport.TestSecurityConfig;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With Redis pointed at a dead port, every cache operation fails — yet all reads and writes still
 * succeed from the authoritative database. This proves the service has no correctness dependency
 * on Redis (ADR-0003 / ADR-0007). Redis intentionally is NOT started here.
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "tcs.security.jwt-mode=local-keys",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + TestTokenIssuer.ISSUER,
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6399" // nothing listens here
})
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@org.junit.jupiter.api.Tag("integration")
class RedisFallbackIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    private void adminPost(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void lookupsSucceedFromTheDatabaseWhenRedisIsDown() throws Exception {
        adminPost("/api/v1/exchanges", """
                { "mic": "XNAS", "name": "Nasdaq", "country": "US", "timezone": "America/New_York" }
                """);
        adminPost("/api/v1/currencies", """
                { "code": "USD", "name": "US Dollar", "minorUnits": 2 }
                """);
        adminPost("/api/v1/instruments", """
                { "instrumentId": "AAPL", "name": "Apple", "instrumentType": "EQUITY",
                  "exchangeMic": "XNAS", "currencyCode": "USD",
                  "identifiers": [ { "source": "ISIN", "value": "US0378331005" } ] }
                """);

        // Cache is unreachable, but the DB answers every read.
        mockMvc.perform(get("/api/v1/instruments/AAPL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.readerBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("AAPL"));

        mockMvc.perform(get("/api/v1/instruments/by-identifier")
                        .param("source", "ISIN").param("value", "US0378331005")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.readerBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("AAPL"));

        mockMvc.perform(get("/api/v1/currencies/USD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EnrichmentTestTokens.readerBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USD"));
    }
}
