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
        String admin = AccountTestTokens.adminBearer();
        String funded = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");
        transition(admin, funded, "activate");
        postCash(admin, funded, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD" }
                """).andExpect(status().isCreated());
        postSecurity(admin, funded, """
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
    void investorUserTokenCannotReachTheCrossOwnerEndpoint() throws Exception {
        // An investor's admin-web token carries account:balance (a user scope) but not the
        // machine scope account:validate / allocation:process — it must be refused, so it can
        // never enumerate other investors' accounts through this endpoint.
        String body = "{ \"items\": [ { \"accountId\": \"ACC-1\" } ] }";
        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.investorBearer("investor1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
