package com.tradingcapture.account;

import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end account lifecycle, ownership masking, and admin visibility over real MySQL. Investors
 * may open their own accounts (PENDING) and read them; every lifecycle transition, rename, and
 * ledger post is ADMIN-only.
 */
class AccountLifecycleIntegrationTest extends IntegrationTestSupport {

    @Test
    void fullLifecyclePendingToClosed() throws Exception {
        String investor = AccountTestTokens.investorBearer("investor1");
        String admin = AccountTestTokens.adminBearer();
        String id = createAccount(investor, "Cash", "USD");

        // PENDING on creation
        mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        transition(admin, id, "activate");
        transition(admin, id, "suspend");
        transition(admin, id, "activate");
        // No balances → closable
        transition(admin, id, "close");

        mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + investor))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void illegalTransitionIsRejected() throws Exception {
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");

        // PENDING cannot be suspended directly
        mockMvc.perform(post("/api/v1/accounts/" + id + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccountTestTokens.adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void closingIsBlockedWhileTheAccountHoldsCash() throws Exception {
        String admin = AccountTestTokens.adminBearer();
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");
        transition(admin, id, "activate");
        postCash(admin, id, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "100.00", "currency": "USD" }
                """).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts/" + id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_EMPTY"));
    }

    @Test
    void ownerInvestorCannotTransitionRenameOrPostToTheirOwnAccount() throws Exception {
        String investor = AccountTestTokens.investorBearer("investor1");
        String admin = AccountTestTokens.adminBearer();
        String id = createAccount(investor, "Cash", "USD");
        transition(admin, id, "activate");
        transition(admin, id, "suspend");

        // A suspension (e.g. compliance) cannot be lifted, nor any other transition made, by the owner.
        for (String action : new String[] {"activate", "suspend", "close"}) {
            mockMvc.perform(post("/api/v1/accounts/" + id + "/" + action)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"accountName\": \"Renamed\" }"))
                .andExpect(status().isForbidden());

        transition(admin, id, "activate");
        // The owner cannot mint cash or securities into their own account.
        postCash(investor, id, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000000.00", "currency": "USD" }
                """).andExpect(status().isForbidden());
        postSecurity(investor, id, """
                { "instrumentId": "AAPL", "direction": "RECEIPT", "transactionType": "TRANSFER_IN", "quantity": "1000" }
                """).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountName").value("Cash"));
        mockMvc.perform(get("/api/v1/accounts/" + id + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalances.length()").value(0))
                .andExpect(jsonPath("$.securityPositions.length()").value(0));
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
