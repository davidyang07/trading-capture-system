package com.tradingcapture.account;

import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end account lifecycle, ownership masking, and admin visibility over real MySQL. */
class AccountLifecycleIntegrationTest extends IntegrationTestSupport {

    @Test
    void fullLifecyclePendingToClosed() throws Exception {
        String bearer = AccountTestTokens.investorBearer("investor1");
        String id = createAccount(bearer, "Cash", "USD");

        // PENDING on creation
        mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        transition(bearer, id, "activate");
        transition(bearer, id, "suspend");
        transition(bearer, id, "activate");
        // No balances → closable
        transition(bearer, id, "close");

        mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void illegalTransitionIsRejected() throws Exception {
        String bearer = AccountTestTokens.investorBearer("investor1");
        String id = createAccount(bearer, "Cash", "USD");

        // PENDING cannot be suspended directly
        mockMvc.perform(post("/api/v1/accounts/" + id + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void closingIsBlockedWhileTheAccountHoldsCash() throws Exception {
        String bearer = AccountTestTokens.investorBearer("investor1");
        String id = createAccount(bearer, "Cash", "USD");
        transition(bearer, id, "activate");
        postCash(bearer, id, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "100.00", "currency": "USD" }
                """).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts/" + id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_EMPTY"));
    }

    @Test
    void anotherInvestorSeesNotFoundNotForbidden() throws Exception {
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");

        mockMvc.perform(get("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.investorBearer("investor2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void adminSeesAnyAccount() throws Exception {
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");

        mockMvc.perform(get("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerSubject").value("investor1"));
    }

    @Test
    void investorSearchIsRestrictedToOwnAccounts() throws Exception {
        createAccount(AccountTestTokens.investorBearer("investor1"), "Cash A", "USD");
        createAccount(AccountTestTokens.investorBearer("investor2"), "Cash B", "USD");

        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.investorBearer("investor1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].ownerSubject").value("investor1"));
    }
}
