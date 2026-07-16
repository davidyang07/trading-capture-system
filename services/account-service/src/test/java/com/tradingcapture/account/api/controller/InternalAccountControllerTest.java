package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.service.validation.AccountValidationService;
import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalAccountController.class)
class InternalAccountControllerTest extends ControllerSliceTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountValidationService validationService;

    private static final String BATCH_BODY = """
            { "items": [ { "accountId": "ACC-1", "requiredCurrency": "USD", "requiredCashAmount": "500.00" } ] }
            """;

    @Test
    void machineTokenGetsPerItemVerdicts() throws Exception {
        when(validationService.validate(anyList())).thenReturn(List.of(
                new AccountValidationService.Outcome("ACC-1", true, AccountStatus.ACTIVE, true,
                        "investor1", true, null, List.of())));

        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .with(auth(AccountTestTokens.allocationMachineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].accountId").value("ACC-1"))
                .andExpect(jsonPath("$.results[0].active").value(true))
                .andExpect(jsonPath("$.results[0].sufficientCash").value(true));
    }

    @Test
    void tradingServiceBalanceScopeIsAlsoAccepted() throws Exception {
        when(validationService.validate(anyList())).thenReturn(List.of(
                new AccountValidationService.Outcome("ACC-1", true, AccountStatus.ACTIVE, true,
                        "investor1", null, null, List.of())));

        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .with(auth(AccountTestTokens.tradingMachineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithoutAServiceScopeIsForbidden() throws Exception {
        var adminOnly = TestJwts.jwt("sub-x", "tcs-admin",
                List.of("ADMIN"), "account:admin", List.of(AccountTestTokens.AUDIENCE));

        mockMvc.perform(post("/internal/v1/accounts/batch").with(auth(adminOnly))
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyBatchIsRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/accounts/batch")
                        .with(auth(AccountTestTokens.allocationMachineJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest());
    }
}
