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
 * Optimistic-locking under real concurrency: two simultaneous debits against one account must
 * never overdraw it — the account's force-incremented version serializes them, so the loser
 * fails with a conflict and the ledger stays consistent (no double-spend).
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerService ledgerService;

    @Test
    void concurrentDebitsCannotOverdrawTheAccount() throws Exception {
        String bearer = AccountTestTokens.investorBearer("investor1");
        String id = createAccount(bearer, "Cash", "USD");
        transition(bearer, id, "activate");
        postCash(bearer, id, """
                { "direction": "CREDIT", "transactionType": "DEPOSIT", "amount": "1000.00", "currency": "USD" }
                """).andExpect(status().isCreated());

        AccessContext ctx = new AccessContext("investor1", false);
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
