package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import com.tradingcapture.account.domain.model.SecurityTransactionType;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.repository.CashTransactionRepository;
import com.tradingcapture.account.domain.repository.SecurityTransactionRepository;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import com.tradingcapture.account.domain.service.command.LedgerCommands;
import com.tradingcapture.account.observability.AccountMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountRepository accounts;
    @Mock
    private CashTransactionRepository cashTransactions;
    @Mock
    private SecurityTransactionRepository securityTransactions;
    @Mock
    private BalanceService balanceService;
    @Mock
    private AccountMetrics metrics;

    private LedgerService service() {
        return new LedgerService(accounts, cashTransactions, securityTransactions, balanceService, metrics, CLOCK);
    }

    private static AccessContext investor(String subject) {
        return new AccessContext(subject, false);
    }

    private static Account activeAccount(String owner) {
        Account account = new Account("ACC-1", owner, "Cash", AccountType.CASH, "USD", CLOCK.instant());
        account.applyStatus(AccountStatus.ACTIVE, CLOCK.instant());
        return account;
    }

    private static LedgerCommands.PostCash cash(CashDirection direction, String amount) {
        return new LedgerCommands.PostCash(direction, CashTransactionType.DEPOSIT,
                new BigDecimal(amount), "USD", null, null, null);
    }

    @Test
    void creditIsAppendedToAnActiveAccount() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));

        LedgerPost<CashTransaction> result =
                service().postCash(investor("investor1"), "ACC-1", cash(CashDirection.CREDIT, "1000.00"));

        assertThat(result.replayed()).isFalse();
        verify(cashTransactions).save(any(CashTransaction.class));
        verify(metrics).cashPosted();
    }

    @Test
    void debitIsRejectedWhenFundsAreInsufficient() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));
        when(balanceService.cashBalance("ACC-1", "USD")).thenReturn(new BigDecimal("500.0000"));

        assertThatThrownBy(() -> service().postCash(investor("investor1"), "ACC-1", cash(CashDirection.DEBIT, "1000.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        verify(cashTransactions, never()).save(any());
        verify(metrics).insufficientFunds();
    }

    @Test
    void debitWithinBalanceIsAppended() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));
        when(balanceService.cashBalance("ACC-1", "USD")).thenReturn(new BigDecimal("5000.0000"));

        LedgerPost<CashTransaction> result =
                service().postCash(investor("investor1"), "ACC-1", cash(CashDirection.DEBIT, "1000.00"));

        assertThat(result.replayed()).isFalse();
        verify(cashTransactions).save(any(CashTransaction.class));
    }

    @Test
    void postToNonActiveAccountIsRejected() {
        Account pending = new Account("ACC-1", "investor1", "Cash", AccountType.CASH, "USD", CLOCK.instant());
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().postCash(investor("investor1"), "ACC-1", cash(CashDirection.CREDIT, "10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void unownedAccountIsMaskedAsNotFound() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));

        assertThatThrownBy(() -> service().postCash(investor("investor2"), "ACC-1", cash(CashDirection.CREDIT, "10.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void idempotentReplayReturnsTheOriginalRowWithoutAppending() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));
        CashTransaction existing = new CashTransaction("CT-1", "ACC-1", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.0000"), "USD", null, "idem-1", null, CLOCK.instant());
        when(cashTransactions.findByAccountIdAndIdempotencyKey("ACC-1", "idem-1")).thenReturn(Optional.of(existing));

        LedgerCommands.PostCash command = new LedgerCommands.PostCash(CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.00"), "USD", null, "idem-1", null);
        LedgerPost<CashTransaction> result = service().postCash(investor("investor1"), "ACC-1", command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isSameAs(existing);
        verify(cashTransactions, never()).save(any());
        verify(metrics).idempotentReplay();
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentPostingIsRefused() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));
        CashTransaction existing = new CashTransaction("CT-1", "ACC-1", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.0000"), "USD", null, "idem-1", null, CLOCK.instant());
        when(cashTransactions.findByAccountIdAndIdempotencyKey("ACC-1", "idem-1")).thenReturn(Optional.of(existing));

        LedgerCommands.PostCash differentAmount = new LedgerCommands.PostCash(CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("5000.00"), "USD", null, "idem-1", null);

        assertThatThrownBy(() -> service().postCash(investor("investor1"), "ACC-1", differentAmount))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).errorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(cashTransactions, never()).save(any());
        verify(metrics, never()).idempotentReplay();
    }

    @Test
    void deliveryIsRejectedWhenPositionIsInsufficient() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));
        when(balanceService.securityPosition("ACC-1", "AAPL")).thenReturn(new BigDecimal("50.0000"));

        LedgerCommands.PostSecurity command = new LedgerCommands.PostSecurity(SecurityDirection.DELIVERY,
                SecurityTransactionType.TRADE_SETTLEMENT, "AAPL", new BigDecimal("100"), null, null, null);

        assertThatThrownBy(() -> service().postSecurity(investor("investor1"), "ACC-1", command))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_SECURITIES);
        verify(securityTransactions, never()).save(any());
        verify(metrics).insufficientSecurities();
    }

    @Test
    void receiptIsAppended() {
        when(accounts.findByIdForUpdate("ACC-1")).thenReturn(Optional.of(activeAccount("investor1")));

        LedgerCommands.PostSecurity command = new LedgerCommands.PostSecurity(SecurityDirection.RECEIPT,
                SecurityTransactionType.TRADE_SETTLEMENT, "AAPL", new BigDecimal("100"), null, null, null);
        LedgerPost<SecurityTransaction> result = service().postSecurity(investor("investor1"), "ACC-1", command);

        assertThat(result.replayed()).isFalse();
        verify(securityTransactions).save(any(SecurityTransaction.class));
        verify(metrics).securityPosted();
    }
}
