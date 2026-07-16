package com.tradingcapture.account.domain.service.validation;

import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Mock
    private AccountRepository accounts;
    @Mock
    private BalanceService balanceService;

    private AccountValidationService service() {
        return new AccountValidationService(accounts, balanceService);
    }

    private static Account active(String id, String owner) {
        Account account = new Account(id, owner, "Cash", AccountType.CASH, "USD", NOW);
        account.applyStatus(AccountStatus.ACTIVE, NOW);
        return account;
    }

    @Test
    void unknownAccountIsReportedNotFoundWithoutFailingTheBatch() {
        when(accounts.findAllById(anyList())).thenReturn(List.of(active("ACC-1", "investor1")));
        lenient().when(balanceService.cashBalance("ACC-1", "USD")).thenReturn(new BigDecimal("1000.0000"));

        List<AccountValidationService.Outcome> outcomes = service().validate(List.of(
                new AccountValidationService.Check("ACC-1", "USD", new BigDecimal("500"), null, null),
                new AccountValidationService.Check("ACC-MISSING", null, null, null, null)));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).found()).isTrue();
        assertThat(outcomes.get(0).sufficientCash()).isTrue();
        assertThat(outcomes.get(1).found()).isFalse();
        assertThat(outcomes.get(1).reasons()).anyMatch(r -> r.contains("does not exist"));
    }

    @Test
    void insufficientCashIsFlagged() {
        when(accounts.findAllById(anyList())).thenReturn(List.of(active("ACC-1", "investor1")));
        when(balanceService.cashBalance("ACC-1", "USD")).thenReturn(new BigDecimal("100.0000"));

        List<AccountValidationService.Outcome> outcomes = service().validate(List.of(
                new AccountValidationService.Check("ACC-1", "USD", new BigDecimal("500"), null, null)));

        assertThat(outcomes.get(0).sufficientCash()).isFalse();
        assertThat(outcomes.get(0).reasons()).anyMatch(r -> r.contains("Insufficient USD"));
    }

    @Test
    void suspendedAccountIsReportedInactive() {
        Account suspended = active("ACC-1", "investor1");
        suspended.applyStatus(AccountStatus.SUSPENDED, NOW);
        when(accounts.findAllById(anyList())).thenReturn(List.of(suspended));

        List<AccountValidationService.Outcome> outcomes = service().validate(List.of(
                new AccountValidationService.Check("ACC-1", null, null, null, null)));

        assertThat(outcomes.get(0).active()).isFalse();
        assertThat(outcomes.get(0).status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void insufficientSecuritiesIsFlagged() {
        when(accounts.findAllById(anyList())).thenReturn(List.of(active("ACC-1", "investor1")));
        when(balanceService.securityPosition("ACC-1", "AAPL")).thenReturn(new BigDecimal("10.0000"));

        List<AccountValidationService.Outcome> outcomes = service().validate(List.of(
                new AccountValidationService.Check("ACC-1", null, null, "AAPL", new BigDecimal("100"))));

        assertThat(outcomes.get(0).sufficientSecurities()).isFalse();
    }
}
