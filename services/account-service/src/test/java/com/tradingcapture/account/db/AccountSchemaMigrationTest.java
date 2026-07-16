package com.tradingcapture.account.db;

import com.tradingcapture.testsupport.MySqlContainerSupport;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway verification against real MySQL: both migrations initialize cleanly on an empty
 * schema, the deliberate constraints exist and are enforced, and the seed's ledger rows sum to
 * the balances a demo expects.
 */
@Tag("integration")
class AccountSchemaMigrationTest extends MySqlContainerSupport {

    private static final String VERIFY_DB = "tcs_account_migration_verify";
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

        Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(verifyUrl, "root", mysql().getPassword());
    }

    @Test
    void bothMigrationsApplyCleanlyOnAnEmptySchema() throws SQLException {
        MigrationInfo[] applied = Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().info().applied();

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
            assertThat(names).contains("accounts", "cash_transactions", "security_transactions", "flyway_schema_history");
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
                    "uq_cash_idempotency",
                    "uq_security_idempotency",
                    "fk_cash_account",
                    "fk_security_account",
                    "chk_cash_amount",
                    "chk_cash_direction",
                    "chk_security_quantity",
                    "chk_accounts_status");
        }
    }

    @Test
    void amountCheckRejectsNonPositiveRows() throws SQLException {
        String insert = """
                INSERT INTO cash_transactions
                    (transaction_ref, account_id, direction, transaction_type, amount, currency, posted_at)
                VALUES ('CT-CHK-BAD', 'ACC-SEED-INV1', 'CREDIT', 'DEPOSIT', -1.0000, 'USD', '2026-07-16 00:00:00.000000')
                """;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(insert))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_cash_amount");
        }
    }

    @Test
    void seededLedgerSumsToTheExpectedBalances() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet cash = statement.executeQuery("""
                    SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0) AS bal
                    FROM cash_transactions WHERE account_id = 'ACC-SEED-INV1' AND currency = 'USD'
                    """);
            assertThat(cash.next()).isTrue();
            assertThat(cash.getBigDecimal("bal")).isEqualByComparingTo(new BigDecimal("95000"));

            ResultSet position = statement.executeQuery("""
                    SELECT COALESCE(SUM(CASE WHEN direction = 'RECEIPT' THEN quantity ELSE -quantity END), 0) AS pos
                    FROM security_transactions WHERE account_id = 'ACC-SEED-INV1' AND instrument_id = 'AAPL'
                    """);
            assertThat(position.next()).isTrue();
            assertThat(position.getBigDecimal("pos")).isEqualByComparingTo(new BigDecimal("400"));
        }
    }
}
