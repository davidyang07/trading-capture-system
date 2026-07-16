package com.tradingcapture.enrichment.db;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
 * Flyway verification against real MySQL: both migrations initialize cleanly, the deliberate
 * constraints exist and are enforced, and the seed's key fixtures (unique global IDs, an
 * intentionally-ambiguous exchange symbol) are present.
 */
@Tag("integration")
class EnrichmentSchemaMigrationTest extends MySqlContainerSupport {

    private static final String VERIFY_DB = "tcs_enrichment_migration_verify";
    private static String verifyUrl;

    @BeforeAll
    static void migrateIntoFreshSchema() throws SQLException {
        String rootUrl = mysql().getJdbcUrl().replace("/" + mysql().getDatabaseName(), "/mysql");
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", mysql().getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + VERIFY_DB);
            statement.execute("CREATE DATABASE " + VERIFY_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        verifyUrl = mysql().getJdbcUrl().replace("/" + mysql().getDatabaseName(), "/" + VERIFY_DB);
        Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(verifyUrl, "root", mysql().getPassword());
    }

    @Test
    void bothMigrationsApplyCleanly() {
        MigrationInfo[] applied = Flyway.configure()
                .dataSource(verifyUrl, "root", mysql().getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().info().applied();
        assertThat(applied).hasSize(2);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void deliberateConstraintsExist() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT constraint_name FROM information_schema.table_constraints
                    WHERE table_schema = '%s'
                    """.formatted(VERIFY_DB));
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            assertThat(names).contains(
                    "uq_alt_ids_source_value_exchange",
                    "fk_instruments_exchange",
                    "fk_instruments_currency",
                    "fk_alt_ids_instrument",
                    "chk_instruments_type",
                    "chk_fee_rules_basis",
                    "fk_fee_rules_currency");
        }
    }

    @Test
    void exchangeSymbolUniquenessIsEnforcedPerExchange() throws SQLException {
        // AAPL already carries EXCHANGE_SYMBOL 'AAPL' on XNAS; a second on XNAS must fail.
        String insert = """
                INSERT INTO instrument_alt_ids (instrument_id, source, id_value, exchange_mic, created_at)
                VALUES ('MSFT', 'EXCHANGE_SYMBOL', 'AAPL', 'XNAS', '2026-07-01 00:00:00.000000')
                """;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(insert))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_alt_ids_source_value_exchange");
        }
    }

    @Test
    void seedProvidesAnAmbiguousSymbolAndUniqueGlobalIds() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet ambiguous = statement.executeQuery(
                    "SELECT COUNT(*) FROM instrument_alt_ids WHERE source='EXCHANGE_SYMBOL' AND id_value='XYZ'");
            assertThat(ambiguous.next()).isTrue();
            assertThat(ambiguous.getInt(1)).isEqualTo(2);

            ResultSet instruments = statement.executeQuery("SELECT COUNT(*) FROM instruments");
            assertThat(instruments.next()).isTrue();
            assertThat(instruments.getInt(1)).isEqualTo(7);

            ResultSet feeRules = statement.executeQuery("SELECT COUNT(*) FROM fee_rules WHERE status='ACTIVE'");
            assertThat(feeRules.next()).isTrue();
            assertThat(feeRules.getInt(1)).isEqualTo(4);
        }
    }
}
