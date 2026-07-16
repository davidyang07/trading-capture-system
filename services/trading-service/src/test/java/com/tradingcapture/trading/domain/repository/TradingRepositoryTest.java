package com.tradingcapture.trading.domain.repository;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MySQL repository semantics: Flyway-created constraints (append-only guarantees),
 * FIX-code column values, specifications, and optimistic locking.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration")
@Tag("integration")
class TradingRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private OrderRepository orders;
    @Autowired
    private ExecutionRepository executions;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Order persistedOrder(String orderId, String owner) {
        return orders.saveAndFlush(TradingFixtures.order(orderId, owner, "100.0000"));
    }

    @Test
    void enumsArePersistedAsFixCodes() {
        Order order = persistedOrder("ORD-R1", "investor1");
        executions.saveAndFlush(TradingFixtures.trade(order, "exec_r1", "60", "10.00"));

        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
                "SELECT status, side, order_type, price_type, settle_type FROM orders WHERE order_id = ?",
                "ORD-R1");
        assertThat(orderRow.get("status")).isEqualTo("0");
        assertThat(orderRow.get("side")).isEqualTo("1");
        assertThat(orderRow.get("order_type")).isEqualTo("2");
        assertThat(orderRow.get("price_type")).isEqualTo("1");
        assertThat(orderRow.get("settle_type")).isEqualTo("3");

        Map<String, Object> executionRow = jdbcTemplate.queryForMap(
                "SELECT exec_type, allocation_state, root_execution_id FROM executions WHERE internal_id = ?",
                "exec_r1");
        assertThat(executionRow.get("exec_type")).isEqualTo("F");
        assertThat(executionRow.get("allocation_state")).isEqualTo("UNALLOCATED");
        assertThat(executionRow.get("root_execution_id")).isEqualTo("exec_r1"); // self-root FK works
    }

    @Test
    void decimalScalesSurviveTheRoundTrip() {
        Order order = persistedOrder("ORD-R2", "investor1");
        entityManager.clear();

        Order reloaded = orders.findById("ORD-R2").orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo("189.25");
        assertThat(reloaded.getPrice().scale()).isEqualTo(8);
        assertThat(reloaded.getQuantity().scale()).isEqualTo(4);
        assertThat(reloaded.getCreatedAt()).isEqualTo(order.getCreatedAt());
    }

    @Test
    void execRefIdIsUniquePerTargetForbiddingChainBranches() {
        Order order = persistedOrder("ORD-R3", "investor1");
        Execution trade = executions.saveAndFlush(TradingFixtures.trade(order, "exec_r3", "60", "10.00"));
        executions.saveAndFlush(TradingFixtures.amendment(order, "exec_r3c1",
                ExecutionType.TRADE_CORRECT, trade, "50", "10.50"));

        assertThatThrownBy(() -> executions.saveAndFlush(TradingFixtures.amendment(order, "exec_r3c2",
                ExecutionType.TRADE_CORRECT, trade, "40", "10.25")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void execIdIsUnique() {
        Order order = persistedOrder("ORD-R4", "investor1");
        executions.saveAndFlush(TradingFixtures.trade(order, "exec_r4a", "10", "1.00"));

        Execution duplicate = Execution.builder()
                .internalId("exec_r4b").execId("EXT-exec_r4a") // same external id
                .order(order).execType(ExecutionType.TRADE).rootExecutionId("exec_r4b")
                .ordStatus(OrderStatus.PARTIALLY_FILLED).side(Side.BUY)
                .lastQty(new BigDecimal("5.0000")).lastPx(new BigDecimal("1.00000000"))
                .cumQty(new BigDecimal("15.0000")).leavesQty(new BigDecimal("85.0000"))
                .avgPx(new BigDecimal("1.00000000")).currency("USD")
                .tradeDate(java.time.LocalDate.parse("2026-07-01"))
                .transactTime(TradingFixtures.FIXED_NOW)
                .settleType(order.getSettleType()).settleDate(order.getSettleDate())
                .createdAt(TradingFixtures.FIXED_NOW)
                .build();

        assertThatThrownBy(() -> executions.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderWithExecutionsCannotBeDeletedAtTheDatabaseLevel() {
        Order order = persistedOrder("ORD-R5", "investor1");
        executions.saveAndFlush(TradingFixtures.trade(order, "exec_r5", "60", "10.00"));

        // Native delete proves the FK RESTRICT itself, independent of Hibernate's own guards.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM orders WHERE order_id = ?", "ORD-R5"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraintRejectsInvalidExecTypeAtTheDatabaseLevel() {
        persistedOrder("ORD-R6", "investor1");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO executions (internal_id, exec_id, order_id, exec_type, exec_ref_id,
                    root_execution_id, ord_status, side, last_qty, last_px, cum_qty, leaves_qty,
                    avg_px, currency, trade_date, transact_time, settle_type, settle_date,
                    allocation_state, created_at, updated_at, version)
                VALUES ('exec_bad', 'EXT-bad', 'ORD-R6', 'Z', NULL, 'exec_bad', '0', '1',
                    1.0, 1.0, 1.0, 99.0, 1.0, 'USD', '2026-07-01', '2026-07-01 09:00:00',
                    '0', '2026-07-03', 'UNALLOCATED', '2026-07-01 09:00:00',
                    '2026-07-01 09:00:00', 0)
                """))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void specificationsFilterByOwnerStatusAndSideAcrossTheOrderJoin() {
        // Unique owner subjects keep this test independent of any committed rows the
        // (shared-container) integration suites may have left behind.
        Order owned = persistedOrder("ORD-R7A", "r7-investor1");
        Order foreign = persistedOrder("ORD-R7B", "r7-investor2");
        executions.saveAndFlush(TradingFixtures.trade(owned, "exec_r7a", "10", "1.00"));
        executions.saveAndFlush(TradingFixtures.trade(foreign, "exec_r7b", "10", "1.00"));

        List<Order> investorOrders = orders.findAll(Specification.allOf(
                OrderSpecifications.ownedBy("r7-investor1"),
                OrderSpecifications.hasStatus(OrderStatus.NEW),
                OrderSpecifications.hasSide(Side.BUY)));
        assertThat(investorOrders).extracting(Order::getOrderId).containsExactly("ORD-R7A");

        List<Execution> investorExecutions = executions.findAll(
                ExecutionSpecifications.orderOwnedBy("r7-investor1"));
        assertThat(investorExecutions).extracting(Execution::getInternalId).containsExactly("exec_r7a");
    }

    @Test
    void optimisticLockRejectsStaleWrites() {
        Order order = persistedOrder("ORD-R8", "investor1");
        entityManager.clear();

        Order first = orders.findById("ORD-R8").orElseThrow();
        first.applyFillState(new BigDecimal("10.0000"), TradingFixtures.FIXED_NOW);
        orders.saveAndFlush(first); // bumps version to 1
        entityManager.clear();

        // A stale copy still carrying version 0 must be rejected.
        order.applyFillState(new BigDecimal("20.0000"), TradingFixtures.FIXED_NOW);
        assertThatThrownBy(() -> orders.saveAndFlush(order))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void chainQueriesReturnRowsInWriteOrder() {
        Order order = persistedOrder("ORD-R9", "investor1");
        Execution trade = executions.saveAndFlush(TradingFixtures.trade(order, "exec_r9", "60", "10.00"));
        Execution correction = TradingFixtures.amendment(order, "exec_r9c",
                ExecutionType.TRADE_CORRECT, trade, "50", "10.50");
        executions.saveAndFlush(correction);

        List<Execution> chain = executions.findByRootExecutionIdOrderByCreatedAtAscInternalIdAsc("exec_r9");
        assertThat(chain).extracting(Execution::getInternalId).containsExactly("exec_r9", "exec_r9c");
        assertThat(executions.existsByExecRefId("exec_r9")).isTrue();
        assertThat(executions.existsByExecRefId("exec_r9c")).isFalse();
    }
}
