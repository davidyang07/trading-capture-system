package com.tradingcapture.account.domain.service.balance;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.repository.CashTransactionRepository;
import com.tradingcapture.account.domain.repository.SecurityTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Balances are Σ credits − Σ debits (Σ receipts − Σ deliveries), with null sums treated as zero. */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private CashTransactionRepository cashTransactions;
    @Mock
    private SecurityTransactionRepository securityTransactions;

    private BalanceService service() {
        return new BalanceService(cashTransactions, securityTransactions);
    }

    @Test
    void cashBalanceSubtractsDebitsFromCredits() {
        when(cashTransactions.sumAmount("A", "USD", CashDirection.CREDIT)).thenReturn(new BigDecimal("100000.0000"));
        when(cashTransactions.sumAmount("A", "USD", CashDirection.DEBIT)).thenReturn(new BigDecimal("5000.0000"));

        assertThat(service().cashBalance("A", "USD")).isEqualByComparingTo("95000.0000");
    }

    @Test
    void cashBalanceTreatsNoRowsAsZero() {
        when(cashTransactions.sumAmount("A", "USD", CashDirection.CREDIT)).thenReturn(null);
        when(cashTransactions.sumAmount("A", "USD", CashDirection.DEBIT)).thenReturn(null);

        assertThat(service().cashBalance("A", "USD")).isEqualByComparingTo("0");
    }

    @Test
    void securityPositionSubtractsDeliveriesFromReceipts() {
        when(securityTransactions.sumQuantity("A", "AAPL", SecurityDirection.RECEIPT)).thenReturn(new BigDecimal("500.0000"));
        when(securityTransactions.sumQuantity("A", "AAPL", SecurityDirection.DELIVERY)).thenReturn(new BigDecimal("100.0000"));

        assertThat(service().securityPosition("A", "AAPL")).isEqualByComparingTo("400.0000");
    }

    @Test
    void isEmptyIsTrueOnlyWhenEveryBalanceAndPositionIsZero() {
        when(cashTransactions.findCurrencies("A")).thenReturn(List.of("USD"));
        when(securityTransactions.findInstruments("A")).thenReturn(List.of());
        when(cashTransactions.sumAmount("A", "USD", CashDirection.CREDIT)).thenReturn(new BigDecimal("100.0000"));
        when(cashTransactions.sumAmount("A", "USD", CashDirection.DEBIT)).thenReturn(new BigDecimal("100.0000"));

        assertThat(service().isEmpty("A")).isTrue();
    }

    @Test
    void isEmptyIsFalseWithARemainingBalance() {
        when(cashTransactions.findCurrencies("A")).thenReturn(List.of("USD"));
        when(securityTransactions.findInstruments("A")).thenReturn(List.of());
        when(cashTransactions.sumAmount("A", "USD", CashDirection.CREDIT)).thenReturn(new BigDecimal("100.0000"));
        when(cashTransactions.sumAmount("A", "USD", CashDirection.DEBIT)).thenReturn(BigDecimal.ZERO);

        assertThat(service().isEmpty("A")).isFalse();
    }
}
