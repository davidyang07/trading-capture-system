package com.tradingcapture.account;

import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Machine batch validation over real MySQL: independent per-item verdicts for a mixed batch. */
class BatchValidationIntegrationTest extends IntegrationTestSupport {

    @Test
    void mixedBatchYieldsIndependentPerItemVerdicts() throws Exception {
        String investor1 = AccountTestTokens.investorBearer("investor1");
        String funded = createAccount(investor1, "Cash", "USD");
        transition(investor1, funded, "activate");
        postCash(investor1, funded, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD" }
                """).andExpect(status().isCreated());
        postSecurity(investor1, funded, """
                { "direction": "RECEIPT", "transactionType": "TRADE_SETTLEMENT", "instrumentId": "AAPL", "quantity": "500" }
                """).andExpect(status().isCreated());

        // A second account left PENDING (inactive).
        String pending = createAccount(AccountTestTokens.investorBearer("investor2"), "Cash", "USD");

        String body = """
                { "items": [
                    { "accountId": "%s", "requiredCurrency": "USD", "requiredCashAmount": "500.00" },
                    { "accountId": "%s", "requiredCurrency": "USD", "requiredCashAmount": "5000.00" },
                    { "accountId": "%s", "instrumentId": "AAPL", "requiredQuantity": "100" },
                    { "accountId": "%s" },
                    { "accountId": "ACC-DOES-NOT-EXIST" }
                ] }
                """.formatted(funded, funded, funded, pending);

        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.allocationMachineBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].found").value(true))
                .andExpect(jsonPath("$.results[0].active").value(true))
                .andExpect(jsonPath("$.results[0].sufficientCash").value(true))
                .andExpect(jsonPath("$.results[1].sufficientCash").value(false))
                .andExpect(jsonPath("$.results[2].sufficientSecurities").value(true))
                .andExpect(jsonPath("$.results[3].found").value(true))
                .andExpect(jsonPath("$.results[3].active").value(false))
                .andExpect(jsonPath("$.results[4].found").value(false));
    }

    @Test
    void internalEndpointRejectsATokenWithoutAServiceScope() throws Exception {
        // A token carrying only account:admin (no account:balance, no allocation:process) is
        // forbidden by the /internal/** chain rule.
        String adminOnly = com.tradingcapture.testsupport.TestTokenIssuer.issue(claims -> claims
                .claim("preferred_username", "tcs-admin")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("ADMIN")))
                .claim("scope", "account:admin")
                .audience(java.util.List.of(AccountTestTokens.AUDIENCE)));

        String body = "{ \"items\": [ { \"accountId\": \"ACC-1\" } ] }";
        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminOnly)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
