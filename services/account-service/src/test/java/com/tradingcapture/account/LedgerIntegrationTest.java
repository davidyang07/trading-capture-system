package com.tradingcapture.account;

import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end ledger behavior: balances, sufficiency rules, idempotency — over real MySQL. Postings
 * are made by an admin on investor-owned accounts (ledger writes are ADMIN-only).
 */
class LedgerIntegrationTest extends IntegrationTestSupport {

    /** An investor-owned account, activated by an admin (lifecycle and postings are ADMIN-only). */
    private String activeAccount(String owner, String currency) throws Exception {
        String id = createAccount(AccountTestTokens.investorBearer(owner), "Cash", currency);
        transition(AccountTestTokens.adminBearer(), id, "activate");
        return id;
    }

    private static String cash(String direction, String type, String amount, String currency) {
        return """
                { "direction": "%s", "transactionType": "%s", "amount": "%s", "currency": "%s" }
                """.formatted(direction, type, amount, currency);
    }

    @Test
    void depositsAndWithdrawalsDeriveTheBalance() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");

        postCash(bearer, id, cash("CREDIT", "DEPOSIT", "1000.00", "USD")).andExpect(status().isCreated());
        postCash(bearer, id, cash("DEBIT", "WITHDRAWAL", "250.00", "USD")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/" + id + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalances[0].currency").value("USD"))
                .andExpect(jsonPath("$.cashBalances[0].balance").value(750.0000));
    }

    @Test
    void overdraftIsRejected() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");
        postCash(bearer, id, cash("CREDIT", "DEPOSIT", "100.00", "USD")).andExpect(status().isCreated());

        postCash(bearer, id, cash("DEBIT", "WITHDRAWAL", "500.00", "USD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void postingToASuspendedAccountIsRejected() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");
        transition(bearer, id, "suspend");

        postCash(bearer, id, cash("CREDIT", "DEPOSIT", "100.00", "USD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void securityReceiptAndDeliveryDeriveThePosition() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");

        postSecurity(bearer, id, """
                { "direction": "RECEIPT", "transactionType": "TRADE_SETTLEMENT", "instrumentId": "AAPL", "quantity": "500" }
                """).andExpect(status().isCreated());
        postSecurity(bearer, id, """
                { "direction": "DELIVERY", "transactionType": "TRADE_SETTLEMENT", "instrumentId": "AAPL", "quantity": "100" }
                """).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/" + id + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(jsonPath("$.securityPositions[0].instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.securityPositions[0].quantity").value(400.0000));
    }

    @Test
    void deliveringMoreThanHeldIsRejected() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");
        postSecurity(bearer, id, """
                { "direction": "RECEIPT", "transactionType": "TRADE_SETTLEMENT", "instrumentId": "AAPL", "quantity": "50" }
                """).andExpect(status().isCreated());

        postSecurity(bearer, id, """
                { "direction": "DELIVERY", "transactionType": "TRADE_SETTLEMENT", "instrumentId": "AAPL", "quantity": "100" }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SECURITIES"));
    }

    @Test
    void idempotentReplayReturns200AndDoesNotDoublePost() throws Exception {
        String bearer = AccountTestTokens.adminBearer();
        String id = activeAccount("investor1", "USD");
        String body = """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD",
                  "idempotencyKey": "deposit-42" }
                """;

        postCash(bearer, id, body).andExpect(status().isCreated());
        postCash(bearer, id, body).andExpect(status().isOk()); // replay

        // Balance reflects a single post, not two.
        mockMvc.perform(get("/api/v1/accounts/" + id + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(jsonPath("$.cashBalances[0].balance").value(1000.0000));
    }
}
