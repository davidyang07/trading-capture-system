package com.tradingcapture.account;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.service.AccessContext;
import com.tradingcapture.account.domain.service.LedgerService;
import com.tradingcapture.account.domain.service.command.LedgerCommands;
import com.tradingcapture.account.testsupport.AccountTestTokens;
import com.tradingcapture.account.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ledger postings under real concurrency. The account row lock queues postings to one account:
 * two simultaneous debits can never overdraw it (the second sees the first and is refused), and
 * two simultaneous valid postings both succeed instead of one dying as a database deadlock.
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerService ledgerService;

    @Test
    void concurrentDebitsCannotOverdrawTheAccount() throws Exception {
        String admin = AccountTestTokens.adminBearer();
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");
        transition(admin, id, "activate");
        postCash(admin, id, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD" }
                """).andExpect(status().isCreated());

        AccessContext ctx = new AccessContext("admin1", true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(() -> debit(ctx, id, start)),
                    executor.submit(() -> debit(ctx, id, start)));
            start.countDown();

            int successes = 0;
            for (Future<Throwable> outcome : outcomes) {
                if (outcome.get(30, TimeUnit.SECONDS) == null) {
                    successes++;
                }
            }

            // 800 + 800 > 1000: at most one debit may commit, and one must always commit.
            assertThat(successes).isEqualTo(1);
            BigDecimal debitCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ? AND direction = 'DEBIT'",
                    BigDecimal.class, id);
            assertThat(debitCount).isEqualByComparingTo("1");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentValidPostingsBothSucceed() throws Exception {
        String admin = AccountTestTokens.adminBearer();
        String id = createAccount(AccountTestTokens.investorBearer("investor1"), "Cash", "USD");
        transition(admin, id, "activate");

        AccessContext ctx = new AccessContext("admin1", true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(() -> credit(ctx, id, start)),
                    executor.submit(() -> credit(ctx, id, start)),
                    executor.submit(() -> credit(ctx, id, start)),
                    executor.submit(() -> credit(ctx, id, start)));
            start.countDown();

            for (Future<Throwable> outcome : outcomes) {
                assertThat(outcome.get(30, TimeUnit.SECONDS)).isNull();
            }
            BigDecimal balance = jdbcTemplate.queryForObject(
                    "SELECT SUM(amount) FROM cash_transactions WHERE account_id = ? AND direction = 'CREDIT'",
                    BigDecimal.class, id);
            assertThat(balance).isEqualByComparingTo("400.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable credit(AccessContext ctx, String accountId, CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            ledgerService.postCash(ctx, accountId, new LedgerCommands.PostCash(
                    CashDirection.CREDIT, CashTransactionType.DEPOSIT, new BigDecimal("100.00"),
                    "USD", null, null, "concurrent credit"));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Throwable debit(AccessContext ctx, String accountId, CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            ledgerService.postCash(ctx, accountId, new LedgerCommands.PostCash(
                    CashDirection.DEBIT, CashTransactionType.WITHDRAWAL, new BigDecimal("800.00"),
                    "USD", null, null, "concurrent debit"));
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
