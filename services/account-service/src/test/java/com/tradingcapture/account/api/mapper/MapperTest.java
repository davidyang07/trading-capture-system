package com.tradingcapture.account.api.mapper;

import com.tradingcapture.account.api.dto.AccountResponse;
import com.tradingcapture.account.api.dto.BalancesResponse;
import com.tradingcapture.account.api.dto.CashTransactionResponse;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.service.balance.AccountBalances;
import com.tradingcapture.account.domain.service.balance.CashBalance;
import com.tradingcapture.account.domain.service.balance.SecurityPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Test
    void accountMapsToResponse() {
        Account account = new Account("ACC-1", "investor1", "Cash", AccountType.CASH, "USD", NOW);
        account.applyStatus(AccountStatus.ACTIVE, NOW);

        AccountResponse response = new AccountMapper().toResponse(account);

        assertThat(response.accountId()).isEqualTo("ACC-1");
        assertThat(response.ownerSubject()).isEqualTo("investor1");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.accountType()).isEqualTo(AccountType.CASH);
        assertThat(response.baseCurrency()).isEqualTo("USD");
    }

    @Test
    void cashTransactionMapsToResponse() {
        CashTransaction transaction = new CashTransaction("CT-1", "ACC-1", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.0000"), "USD", "ext-1", "idem-1", "note", NOW);

        CashTransactionResponse response = new TransactionMapper().toResponse(transaction);

        assertThat(response.transactionRef()).isEqualTo("CT-1");
        assertThat(response.direction()).isEqualTo(CashDirection.CREDIT);
        assertThat(response.amount()).isEqualByComparingTo("1000.0000");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.externalRef()).isEqualTo("ext-1");
    }

    @Test
    void balancesMapToResponse() {
        AccountBalances balances = new AccountBalances("ACC-1",
                List.of(new CashBalance("USD", new BigDecimal("95000.0000"))),
                List.of(new SecurityPosition("AAPL", new BigDecimal("400.0000"))));

        BalancesResponse response = new TransactionMapper().toResponse(balances);

        assertThat(response.accountId()).isEqualTo("ACC-1");
        assertThat(response.cashBalances()).singleElement()
                .satisfies(cash -> {
                    assertThat(cash.currency()).isEqualTo("USD");
                    assertThat(cash.balance()).isEqualByComparingTo("95000.0000");
                });
        assertThat(response.securityPositions()).singleElement()
                .satisfies(position -> assertThat(position.instrumentId()).isEqualTo("AAPL"));
    }
}
