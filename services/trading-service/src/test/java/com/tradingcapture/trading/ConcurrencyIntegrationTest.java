package com.tradingcapture.trading;

import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.domain.service.ExecutionService;
import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
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

/**
 * Optimistic-locking under real concurrency: two simultaneous captures against one order
 * must never overfill it — the loser fails with a version/state conflict, the aggregate
 * stays consistent.
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ExecutionService executionService;

    @Test
    void concurrentCapturesCannotOverfillTheOrder() throws Exception {
        String orderId = createOrder(TradingTestTokens.investorBearer("investor1"), "AAPL", "100");
        AccessContext admin = new AccessContext("tcs-admin", true, false);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(() -> capture(admin, orderId, start)),
                    executor.submit(() -> capture(admin, orderId, start)));
            start.countDown();

            int successes = 0;
            for (Future<Throwable> outcome : outcomes) {
                if (outcome.get(30, TimeUnit.SECONDS) == null) {
                    successes++;
                }
            }

            // 60 + 60 > 100: at most one capture may commit, and one must always commit.
            assertThat(successes).isEqualTo(1);
            BigDecimal cumQty = jdbcTemplate.queryForObject(
                    "SELECT cum_qty FROM orders WHERE order_id = ?", BigDecimal.class, orderId);
            Integer executionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM executions WHERE order_id = ?", Integer.class, orderId);
            assertThat(cumQty).isEqualByComparingTo("60");
            assertThat(executionCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable capture(AccessContext ctx, String orderId, CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            executionService.createTrade(ctx, new ExecutionCommands.CreateTrade(
                    orderId, null, new BigDecimal("60"), new BigDecimal("10.00"),
                    null, null, "concurrent capture", null));
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
