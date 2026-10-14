package com.tradingcapture.trading;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.domain.service.AllocationStateService;
import com.tradingcapture.trading.domain.service.ExecutionService;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;
import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import com.tradingcapture.trading.testsupport.IntegrationTestSupport;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locking under real concurrency: two simultaneous captures against one order must never overfill
 * it (optimistic, the loser fails with a version/state conflict), and an ALLOCATE racing a
 * correction/cancellation of the same execution must never leave a replaced or voided execution
 * ALLOCATED (pessimistic row lock on the execution — the second writer waits, then sees the
 * first one's committed outcome).
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final AccessContext ADMIN = new AccessContext("tcs-admin", true, false);

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private AllocationStateService allocationStateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentCapturesCannotOverfillTheOrder() throws Exception {
        String orderId = createOrder(TradingTestTokens.investorBearer("investor1"), "AAPL", "100");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(() -> capture(ADMIN, orderId, start)),
                    executor.submit(() -> capture(ADMIN, orderId, start)));
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

    @Test
    void allocateWaitingOnAnUncommittedCancelSeesItAndRefuses() throws Exception {
        String orderId = createOrder(TradingTestTokens.investorBearer("investor1"), "AAPL", "100");
        String target = captureFill(orderId);

        Throwable allocateOutcome = raceSecondAgainstOpenFirst(
                () -> executionService.cancel(ADMIN, target, new ExecutionCommands.Cancel(null, "bust")),
                () -> allocationStateService.apply(allocate("ALLOC-RACE-1", target)));

        assertThat(allocateOutcome).isInstanceOf(BusinessRuleException.class);
        assertThat(((BusinessRuleException) allocateOutcome).errorCode())
                .isEqualTo(ErrorCode.ALLOCATION_TARGET_INELIGIBLE);
        assertThat(allocationIdOf(target)).isNull();
    }

    @Test
    void cancelWaitingOnAnUncommittedAllocateSeesItAndRefuses() throws Exception {
        String orderId = createOrder(TradingTestTokens.investorBearer("investor1"), "AAPL", "100");
        String target = captureFill(orderId);

        Throwable cancelOutcome = raceSecondAgainstOpenFirst(
                () -> allocationStateService.apply(allocate("ALLOC-RACE-2", target)),
                () -> executionService.cancel(ADMIN, target, new ExecutionCommands.Cancel(null, "bust")));

        assertThat(cancelOutcome).isInstanceOf(BusinessRuleException.class);
        assertThat(((BusinessRuleException) cancelOutcome).errorCode()).isEqualTo(ErrorCode.EXECUTION_ALLOCATED);
        assertThat(allocationIdOf(target)).isEqualTo("ALLOC-RACE-2");
        Integer amendments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM executions WHERE exec_ref_id = ?", Integer.class, target);
        assertThat(amendments).isZero();
    }

    /**
     * Runs {@code first} inside a transaction that stays open while {@code second} starts on
     * another thread, then commits it; returns {@code second}'s failure (null on success). Without
     * the row lock, {@code second} would check eligibility against the uncommitted state and both
     * would commit.
     */
    private Throwable raceSecondAgainstOpenFirst(Runnable first, Runnable second) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstDone = executor.submit(() -> tx.executeWithoutResult(status -> {
                first.run();
                firstApplied.countDown();
                try {
                    commitFirst.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(firstApplied.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> secondDone = executor.submit(() -> {
                try {
                    second.run();
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            Thread.sleep(500); // let the second writer reach the database while the first is open
            commitFirst.countDown();
            firstDone.get(30, TimeUnit.SECONDS);
            return secondDone.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private String captureFill(String orderId) {
        Execution execution = executionService.createTrade(ADMIN, new ExecutionCommands.CreateTrade(
                orderId, null, new BigDecimal("60"), new BigDecimal("10.00"), null, null, "fill", null));
        return execution.getInternalId();
    }

    private static AllocationStateCommand allocate(String allocationId, String internalId) {
        return new AllocationStateCommand(allocationId, AllocationStateCommand.Action.ALLOCATE, List.of(internalId));
    }

    private String allocationIdOf(String internalId) {
        return jdbcTemplate.queryForObject(
                "SELECT allocation_id FROM executions WHERE internal_id = ?", String.class, internalId);
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
