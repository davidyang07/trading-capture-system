package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.InvalidRequestException;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import com.tradingcapture.account.domain.service.command.AccountCommands;
import com.tradingcapture.account.observability.AccountMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountRepository accounts;
    @Mock
    private BalanceService balanceService;
    @Mock
    private AccountMetrics metrics;

    private AccountService service() {
        return new AccountService(accounts, balanceService, metrics, CLOCK);
    }

    private static AccessContext investor(String subject) {
        return new AccessContext(subject, false);
    }

    private static AccessContext admin() {
        return new AccessContext("tcs-admin", true);
    }

    private static Account account(String id, String owner, AccountStatus status) {
        Account account = new Account(id, owner, "Cash", AccountType.CASH, "USD", CLOCK.instant());
        // Drive to the desired status through legal transitions (no test-only production setter).
        switch (status) {
            case PENDING -> { /* initial state */ }
            case ACTIVE -> account.applyStatus(AccountStatus.ACTIVE, CLOCK.instant());
            case SUSPENDED -> {
                account.applyStatus(AccountStatus.ACTIVE, CLOCK.instant());
                account.applyStatus(AccountStatus.SUSPENDED, CLOCK.instant());
            }
            case CLOSED -> account.applyStatus(AccountStatus.CLOSED, CLOCK.instant());
        }
        return account;
    }

    @Test
    void createDefaultsOwnerToTheCallerAndGeneratesAnId() {
        when(accounts.existsById(anyString())).thenReturn(false);
        AccountCommands.CreateAccount command =
                new AccountCommands.CreateAccount(null, null, "My Cash", AccountType.CASH, "USD");

        Account created = service().create(investor("investor1"), command);

        assertThat(created.getOwnerSubject()).isEqualTo("investor1");
        assertThat(created.getAccountId()).startsWith("ACC-");
        assertThat(created.getStatus()).isEqualTo(AccountStatus.PENDING);
        verify(accounts).save(any(Account.class));
        verify(metrics).accountCreated();
    }

    @Test
    void adminMayCreateForAnotherOwner() {
        when(accounts.existsById(anyString())).thenReturn(false);
        AccountCommands.CreateAccount command =
                new AccountCommands.CreateAccount("ACC-X", "investor2", "Their Cash", AccountType.CASH, "USD");

        Account created = service().create(admin(), command);

        assertThat(created.getOwnerSubject()).isEqualTo("investor2");
        assertThat(created.getAccountId()).isEqualTo("ACC-X");
    }

    @Test
    void investorCannotCreateForAnotherOwner() {
        AccountCommands.CreateAccount command =
                new AccountCommands.CreateAccount(null, "investor2", "Their Cash", AccountType.CASH, "USD");

        assertThatThrownBy(() -> service().create(investor("investor1"), command))
                .isInstanceOf(InvalidRequestException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    void duplicateAccountIdIsRejected() {
        when(accounts.existsById("ACC-DUP")).thenReturn(true);
        AccountCommands.CreateAccount command =
                new AccountCommands.CreateAccount("ACC-DUP", null, "Cash", AccountType.CASH, "USD");

        assertThatThrownBy(() -> service().create(investor("investor1"), command))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ACCOUNT_ID);
    }

    @Test
    void getMasksAnotherInvestorsAccountAsNotFound() {
        when(accounts.findById("ACC-1")).thenReturn(Optional.of(account("ACC-1", "investor1", AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> service().get(investor("investor2"), "ACC-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void changeStatusRejectsAnIllegalTransition() {
        when(accounts.findById("ACC-1")).thenReturn(Optional.of(account("ACC-1", "investor1", AccountStatus.PENDING)));

        assertThatThrownBy(() -> service().changeStatus(admin(), "ACC-1", AccountStatus.SUSPENDED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void closeIsBlockedWhileTheAccountHoldsABalance() {
        when(accounts.findById("ACC-1")).thenReturn(Optional.of(account("ACC-1", "investor1", AccountStatus.ACTIVE)));
        when(balanceService.isEmpty("ACC-1")).thenReturn(false);

        assertThatThrownBy(() -> service().changeStatus(admin(), "ACC-1", AccountStatus.CLOSED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_EMPTY);
    }

    @Test
    void closeSucceedsWhenEmpty() {
        Account account = account("ACC-1", "investor1", AccountStatus.ACTIVE);
        when(accounts.findById("ACC-1")).thenReturn(Optional.of(account));
        when(balanceService.isEmpty("ACC-1")).thenReturn(true);

        Account closed = service().changeStatus(admin(), "ACC-1", AccountStatus.CLOSED);

        assertThat(closed.getStatus()).isEqualTo(AccountStatus.CLOSED);
        verify(metrics).accountStatusChanged();
    }

    @Test
    void createPersistsTheResolvedOwner() {
        when(accounts.existsById(anyString())).thenReturn(false);
        AccountCommands.CreateAccount command =
                new AccountCommands.CreateAccount(null, null, "Cash", AccountType.CASH, "USD");

        service().create(investor("investor9"), command);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        assertThat(captor.getValue().getOwnerSubject()).isEqualTo("investor9");
    }
}
