package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.api.mapper.AccountMapper;
import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.service.AccessContext;
import com.tradingcapture.account.domain.service.AccountService;
import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(AccountMapper.class)
class AccountControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private static final String VALID_CREATE_BODY = """
            { "accountName": "My Cash", "accountType": "CASH", "baseCurrency": "USD" }
            """;

    private static Account account(String id, String owner, AccountStatus status) {
        Account account = new Account(id, owner, "My Cash", AccountType.CASH, "USD", Instant.parse("2026-07-16T00:00:00Z"));
        if (status == AccountStatus.ACTIVE) {
            account.applyStatus(AccountStatus.ACTIVE, Instant.parse("2026-07-16T00:00:00Z"));
        }
        return account;
    }

    @Test
    void anonymousRequestsGet401ProblemResponse() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void createRequiresTheAdminScope() throws Exception {
        var balanceOnly = TestJwts.jwt("sub-x", "investor1",
                List.of("INVESTOR"), "account:balance", List.of(AccountTestTokens.AUDIENCE));

        mockMvc.perform(post("/api/v1/accounts").with(auth(balanceOnly))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(accountService.create(any(), any())).thenReturn(account("ACC-1", "investor1", AccountStatus.PENDING));

        mockMvc.perform(post("/api/v1/accounts").with(auth(AccountTestTokens.investorJwt("investor1")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/accounts/ACC-1"))
                .andExpect(jsonPath("$.accountId").value("ACC-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ownerSubject").value("investor1"));

        ArgumentCaptor<AccessContext> ctx = ArgumentCaptor.forClass(AccessContext.class);
        verify(accountService).create(ctx.capture(), any());
        assertThat(ctx.getValue().subject()).isEqualTo("investor1");
        assertThat(ctx.getValue().admin()).isFalse();
    }

    @Test
    void createValidationFailureListsFieldErrors() throws Exception {
        String invalidBody = """
                { "accountType": "CASH", "baseCurrency": "usd" }
                """;

        mockMvc.perform(post("/api/v1/accounts").with(auth(AccountTestTokens.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void unknownOrMaskedAccountMapsTo404() throws Exception {
        when(accountService.get(any(), eq("ACC-X"))).thenThrow(ResourceNotFoundException.account("ACC-X"));

        mockMvc.perform(get("/api/v1/accounts/ACC-X").with(auth(AccountTestTokens.investorJwt("investor1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void searchReturnsPageEnvelope() throws Exception {
        when(accountService.search(any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(account("ACC-1", "investor1", AccountStatus.ACTIVE)), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/v1/accounts").param("status", "ACTIVE").param("size", "5")
                        .with(auth(AccountTestTokens.investorJwt("investor1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountId").value("ACC-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchRejectsInvalidSortField() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("sortBy", "ownerSubject")
                        .with(auth(AccountTestTokens.adminJwt())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidLifecycleTransitionMapsTo409() throws Exception {
        when(accountService.changeStatus(any(), eq("ACC-1"), eq(AccountStatus.SUSPENDED))).thenThrow(
                new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, "illegal"));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/suspend").with(auth(AccountTestTokens.adminJwt())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void lifecycleTransitionRequiresAdminScope() throws Exception {
        var balanceOnly = TestJwts.jwt("sub-x", "tcs-admin",
                List.of("ADMIN"), "account:balance", List.of(AccountTestTokens.AUDIENCE));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/activate").with(auth(balanceOnly)))
                .andExpect(status().isForbidden());
    }

    @Test
    void investorWithTheAdminScopeStillCannotTransitionOrRename() throws Exception {
        // Every admin-web user token carries account:admin; the ADMIN role is what authorizes.
        var investor = AccountTestTokens.investorJwt("investor1");

        for (String action : List.of("activate", "suspend", "close")) {
            mockMvc.perform(post("/api/v1/accounts/ACC-1/" + action).with(auth(investor)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/accounts/ACC-1").with(auth(investor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"accountName\": \"Renamed\" }"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountService);
    }
}
