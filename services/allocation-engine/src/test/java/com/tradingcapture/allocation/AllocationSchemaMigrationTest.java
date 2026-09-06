package com.tradingcapture.allocation;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Flyway schema against real MySQL: entity mappings validate
 * ({@code ddl-auto=validate} runs on context start) and the idempotency/state constraints
 * behave — not merely exist by name (ADR-0007).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class AllocationSchemaMigrationTest extends MySqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private static final String RAW_INSERT = """
            INSERT INTO raw_messages (topic, kafka_partition, kafka_offset, payload,
                correlation_id, status, delivery_count, received_at, created_at, updated_at)
            VALUES (?, ?, ?, 'payload', 'corr', ?, 1, NOW(6), NOW(6), NOW(6))
            """;

    @Test
    void flywayAppliedTheAllocationSchema() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForList("SHOW TABLES", String.class))
                .contains("raw_messages", "allocations", "allocation_orders",
                        "allocation_executions", "allocation_accounts", "allocation_misc_fees",
                        "validation_errors", "validation_error_fields", "outbox_event");
    }

    @Test
    void kafkaCoordinatesAreUniqueAtTheDatabaseLevel() {
        jdbc.update(RAW_INSERT, "t-mig-1", 0, 1L, "RECEIVED");

        assertThatThrownBy(() -> jdbc.update(RAW_INSERT, "t-mig-1", 0, 1L, "RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_raw_messages_coordinates");
    }

    @Test
    void allocationBusinessKeyIsUniqueAtTheDatabaseLevel() {
        jdbc.update(RAW_INSERT, "t-mig-2", 0, 2L, "RECEIVED");
        Long rawId = jdbc.queryForObject(
                "SELECT id FROM raw_messages WHERE topic = 't-mig-2'", Long.class);
        String insert = """
                INSERT INTO allocations (alloc_id, alloc_trans_type, status, raw_message_id,
                    created_at, updated_at, version)
                VALUES ('ALLOC-MIG', '0', 'RECEIVED', ?, NOW(6), NOW(6), 0)
                """;
        jdbc.update(insert, rawId);

        assertThatThrownBy(() -> jdbc.update(insert, rawId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_allocations_business_key");
    }

    @Test
    void statusCheckConstraintsRejectUnknownCodes() {
        // MySQL CHECK violations (error 3819) surface as a generic DataAccessException
        // (Spring's translator has no category for them) — the behavior under test is that
        // the INSERT fails on the named constraint.
        assertThatThrownBy(() -> jdbc.update(RAW_INSERT, "t-mig-3", 0, 3L, "SOMETHING_ELSE"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_raw_messages_status");

        jdbc.update(RAW_INSERT, "t-mig-4", 0, 4L, "RECEIVED");
        Long rawId = jdbc.queryForObject(
                "SELECT id FROM raw_messages WHERE topic = 't-mig-4'", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO allocations (alloc_id, alloc_trans_type, status, raw_message_id,
                    created_at, updated_at, version)
                VALUES ('ALLOC-MIG-4', '0', 'PARSE_FAILED', ?, NOW(6), NOW(6), 0)
                """, rawId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_allocations_status");
    }

    @Test
    void outboxEventIdIsUnique() {
        String insert = """
                INSERT INTO outbox_event (event_id, event_type, aggregate_type, aggregate_id,
                    topic, message_key, payload, correlation_id, attempts, created_at)
                VALUES ('evt-mig-1', 'AllocationReceived', 'ALLOCATION', 'ALLOC-MIG',
                    'tcs.allocation.allocation-received.v1', 'ALLOC-MIG', x'00', 'corr', 0, NOW(6))
                """;
        jdbc.update(insert);

        assertThatThrownBy(() -> jdbc.update(insert))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_outbox_event_event_id");
    }
}
