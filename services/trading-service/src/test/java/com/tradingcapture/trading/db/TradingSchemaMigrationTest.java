package com.tradingcapture.trading.db;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.service.OrderFillMath;
import com.tradingcapture.trading.domain.service.OrderFillMath.FillEvent;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway verification against real MySQL: both migrations initialize cleanly on an empty
 * schema, the deliberate constraints exist, and the seed data is internally consistent —
 * every seeded order's stored aggregates equal what the service would derive from its
 * seeded execution history (via the production {@link OrderFillMath}).
 */
@Tag("integration")
class TradingSchemaMigrationTest extends MySqlContainerSupport {

    private static final String VERIFY_DB = "tcs_trading_migration_verify";
    private static String verifyUrl;

    @BeforeAll
    static void migrateIntoFreshSchema() throws SQLException {
        String rootUrl = mysql().getJdbcUrl().replace("/" + mysql().getDatabaseName(), "/mysql");
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", mysql().getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + VERIFY_DB);
            statement.execute("CREATE DATABASE " + VERIFY_DB
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        verifyUrl = mysql().getJdbcUrl().replace("/" + mysql().getDatabaseName(), "/" + VERIFY_DB);

        Flyway flyway = Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load();
        flyway.migrate();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(verifyUrl, "root", mysql().getPassword());
    }

    @Test
    void bothMigrationsApplyCleanlyOnAnEmptySchema() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load();
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).hasSize(2);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet tables = statement.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = '%s' ORDER BY table_name
                    """.formatted(VERIFY_DB));
            List<String> names = new ArrayList<>();
            while (tables.next()) {
                names.add(tables.getString(1));
            }
            assertThat(names).contains("orders", "executions", "execution_misc_fees", "flyway_schema_history");
        }
    }

    @Test
    void deliberateConstraintsExist() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet constraints = statement.executeQuery("""
                    SELECT constraint_name FROM information_schema.table_constraints
                    WHERE table_schema = '%s'
                    """.formatted(VERIFY_DB));
            List<String> names = new ArrayList<>();
            while (constraints.next()) {
                names.add(constraints.getString(1));
            }
            assertThat(names).contains(
                    "uq_executions_exec_id",
                    "uq_executions_exec_ref_id",
                    "fk_executions_order",
                    "fk_executions_exec_ref",
                    "fk_executions_root",
                    "chk_executions_ref_presence",
                    "chk_executions_allocation_pair",
                    "chk_orders_cum_qty",
                    "fk_execution_misc_fees_execution");
        }
    }

    @Test
    void allocationPairCheckRejectsAllocatedRowWithoutAllocationId() throws SQLException {
        // ALLOCATED requires a non-null allocation_id; everything else in the row is valid,
        // so a failure here proves chk_executions_allocation_pair is actually enforced, not
        // merely present by name. (FKs point at existing seed rows.)
        String insert = """
                INSERT INTO executions
                  (internal_id, exec_id, order_id, exec_type, root_execution_id, ord_status,
                   side, last_qty, cum_qty, leaves_qty, currency, trade_date, transact_time,
                   settle_type, settle_date, allocation_state, allocation_id, created_at, updated_at)
                VALUES
                  ('chk_alloc_bad', 'CHK-ALLOC-BAD', 'ORD-SEED-001', 'F', 'exec_seed_001', '0',
                   '1', 0, 0, 0, 'USD', '2026-01-01', '2026-01-01 00:00:00.000000',
                   '0', '2026-01-01', 'ALLOCATED', NULL, '2026-01-01 00:00:00.000000',
                   '2026-01-01 00:00:00.000000')
                """;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(insert))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_executions_allocation_pair");
        }
    }

    @Test
    void seededOrderAggregatesMatchWhatTheServiceWouldDerive() throws SQLException {
        Map<String, List<FillEvent>> eventsByOrder = new HashMap<>();
        Map<String, OrderSnapshot> orderSnapshots = new HashMap<>();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet orderRows = statement.executeQuery(
                    "SELECT order_id, status, quantity, cum_qty, leaves_qty FROM orders");
            while (orderRows.next()) {
                orderSnapshots.put(orderRows.getString("order_id"), new OrderSnapshot(
                        orderRows.getString("status"),
                        orderRows.getBigDecimal("quantity"),
                        orderRows.getBigDecimal("cum_qty"),
                        orderRows.getBigDecimal("leaves_qty")));
            }
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet executionRows = statement.executeQuery(
                    "SELECT internal_id, exec_ref_id, exec_type, order_id, last_qty, last_px FROM executions");
            while (executionRows.next()) {
                eventsByOrder.computeIfAbsent(executionRows.getString("order_id"), key -> new ArrayList<>())
                        .add(new FillEvent(executionRows.getString("internal_id"),
                                executionRows.getString("exec_ref_id"),
                                ExecutionType.fromCode(executionRows.getString("exec_type")),
                                executionRows.getBigDecimal("last_qty"),
                                executionRows.getBigDecimal("last_px")));
            }
        }

        assertThat(orderSnapshots).hasSize(6);
        assertThat(eventsByOrder.values().stream().mapToInt(List::size).sum()).isEqualTo(8);

        for (Map.Entry<String, OrderSnapshot> entry : orderSnapshots.entrySet()) {
            OrderSnapshot snapshot = entry.getValue();
            List<FillEvent> events = eventsByOrder.getOrDefault(entry.getKey(), List.of());
            BigDecimal derivedCum = OrderFillMath.compute(events).cumQty();

            assertThat(snapshot.cumQty)
                    .as("cum_qty of %s", entry.getKey())
                    .isEqualByComparingTo(derivedCum);
            assertThat(snapshot.leavesQty)
                    .as("leaves_qty of %s", entry.getKey())
                    .isEqualByComparingTo(snapshot.quantity.subtract(derivedCum));

            String expectedStatus = derivedCum.signum() == 0 ? "0"
                    : derivedCum.compareTo(snapshot.quantity) == 0 ? "2" : "1";
            assertThat(snapshot.status)
                    .as("status of %s", entry.getKey())
                    .isEqualTo(expectedStatus);
        }
    }

    @Test
    void seedChainsPersistDirectAndRootReferences() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet correction = statement.executeQuery(
                    "SELECT exec_ref_id, root_execution_id FROM executions WHERE internal_id = 'exec_seed_005'");
            assertThat(correction.next()).isTrue();
            assertThat(correction.getString("exec_ref_id")).isEqualTo("exec_seed_004");
            assertThat(correction.getString("root_execution_id")).isEqualTo("exec_seed_004");

            ResultSet cancellation = statement.executeQuery(
                    "SELECT exec_ref_id, root_execution_id, last_px FROM executions WHERE internal_id = 'exec_seed_007'");
            assertThat(cancellation.next()).isTrue();
            assertThat(cancellation.getString("exec_ref_id")).isEqualTo("exec_seed_006");
            assertThat(cancellation.getString("root_execution_id")).isEqualTo("exec_seed_006");
            assertThat(cancellation.getBigDecimal("last_px")).isNull();

            ResultSet allocated = statement.executeQuery(
                    "SELECT allocation_state, allocation_id FROM executions WHERE internal_id = 'exec_seed_008'");
            assertThat(allocated.next()).isTrue();
            assertThat(allocated.getString("allocation_state")).isEqualTo("ALLOCATED");
            assertThat(allocated.getString("allocation_id")).isEqualTo("ALLOC-SEED-001");
        }
    }

    private record OrderSnapshot(String status, BigDecimal quantity, BigDecimal cumQty, BigDecimal leavesQty) {
    }
}
