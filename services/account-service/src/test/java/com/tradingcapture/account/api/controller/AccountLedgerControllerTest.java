package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.api.mapper.TransactionMapper;
import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.service.LedgerPost;
import com.tradingcapture.account.domain.service.LedgerService;
import com.tradingcapture.account.domain.service.balance.AccountBalances;
import com.tradingcapture.account.domain.service.balance.CashBalance;
import com.tradingcapture.account.domain.service.balance.SecurityPosition;
import com.tradingcapture.account.testsupport.AccountTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountLedgerController.class)
@Import(TransactionMapper.class)
class AccountLedgerControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    private static final String CASH_BODY = """
            { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD" }
            """;

    private static CashTransaction cash() {
        return new CashTransaction("CT-1", "ACC-1", CashDirection.CREDIT, CashTransactionType.DEPOSIT,
                new BigDecimal("1000.0000"), "USD", null, null, null, Instant.parse("2026-07-16T00:00:00Z"));
    }

    @Test
    void postCashReturns201WhenCreated() throws Exception {
        when(ledgerService.postCash(any(), eq("ACC-1"), any())).thenReturn(LedgerPost.created(cash()));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions")
                        .with(auth(AccountTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionRef").value("CT-1"))
                .andExpect(jsonPath("$.direction").value("CREDIT"));
    }

    @Test
    void idempotentReplayReturns200() throws Exception {
        when(ledgerService.postCash(any(), eq("ACC-1"), any())).thenReturn(LedgerPost.replayed(cash()));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions")
                        .with(auth(AccountTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value("CT-1"));
    }

    @Test
    void insufficientFundsMapsTo409() throws Exception {
        when(ledgerService.postCash(any(), eq("ACC-1"), any())).thenThrow(
                new BusinessRuleException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient USD funds"));

        String debitBody = CASH_BODY.replace("CREDIT", "DEBIT").replace("DEPOSIT", "WITHDRAWAL");
        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions")
                        .with(auth(AccountTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(debitBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void negativeAmountIsRejected() throws Exception {
        String badBody = CASH_BODY.replace("1000.00", "-5.00");
        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions")
                        .with(auth(AccountTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void postingRequiresTheBalanceScope() throws Exception {
        var noScope = com.tradingcapture.testsupport.TestJwts.jwt("sub-x", "tcs-admin",
                List.of("ADMIN"), "account:admin", List.of(AccountTestTokens.AUDIENCE));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions").with(auth(noScope))
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void investorCannotPostCashEvenToTheirOwnAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-1/cash-transactions")
                        .with(auth(AccountTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ledgerService);
    }

    @Test
    void investorCannotPostSecurities() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-1/security-transactions")
                        .with(auth(AccountTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "direction": "RECEIPT", "transactionType": "TRANSFER_IN",
                                  "instrumentId": "AAPL", "quantity": "1000" }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ledgerService);
    }

    @Test
    void balancesReturnsLedgerDerivedFigures() throws Exception {
        when(ledgerService.balances(any(), eq("ACC-1"))).thenReturn(new AccountBalances("ACC-1",
                List.of(new CashBalance("USD", new BigDecimal("95000.0000"))),
                List.of(new SecurityPosition("AAPL", new BigDecimal("400.0000")))));

        mockMvc.perform(get("/api/v1/accounts/ACC-1/balances")
                        .with(auth(AccountTestTokens.investorJwt("investor1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalances[0].currency").value("USD"))
                .andExpect(jsonPath("$.cashBalances[0].balance").value(95000.0000))
                .andExpect(jsonPath("$.securityPositions[0].instrumentId").value("AAPL"));
    }
}
