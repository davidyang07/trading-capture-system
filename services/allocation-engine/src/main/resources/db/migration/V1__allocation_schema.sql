-- Trading Capture System - tcs_allocation schema (Phase 7).
-- Flyway is the only owner of this schema (ddl-auto=validate). The schema name comes from
-- the JDBC connection (tcs_allocation in compose, container DB in tests), so no statement
-- below references a database name.
--
-- Type standards (docs/architecture/data-ownership.md / ADR-0014):
--   money & quantities DECIMAL(19,4) | prices & rates DECIMAL(19,8)
--   timestamps DATETIME(6) stored UTC | enums as explicit string codes
--
-- Code domains (JPA converters own the mapping):
--   raw_messages.status      : RECEIVED PROCESSED INVALID PARSE_FAILED DUPLICATE FAILED
--   raw_messages.failure_category :
--       MALFORMED_MESSAGE UNSUPPORTED_MESSAGE_TYPE FIELD_VALIDATION RETRY_EXHAUSTED
--   allocations.status (ADR-0006 state machine; Phase 7 reaches RECEIVED/INVALID only):
--       RECEIVED INVALID VALIDATED REJECTED EXECUTIONS_ALLOCATED EXECUTIONS_RELEASED
--       COMPLETED FAILED
--   allocations.alloc_trans_type : '0'=NEW '1'=REPLACE '2'=CANCEL (FIX 71)
--   validation_errors.error_code (ADR-0012 taxonomy):
--       MALFORMED_MESSAGE UNSUPPORTED_MESSAGE_TYPE MISSING_REQUIRED_FIELD
--       INVALID_VALUE_FORMAT VALUE_NOT_SUPPORTED VALUE_OUT_OF_BOUND CONFLICTING_FIELDS

-- Inbound audit log: every consumed Kafka record, verbatim, before any destructive
-- parsing (ADR-0007). uq_raw_messages_coordinates absorbs broker redelivery; rows are
-- immutable except the processing-state pair (status, failure_*, processed_at,
-- delivery_count, updated_at).
CREATE TABLE raw_messages (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    topic            VARCHAR(255)   NOT NULL,
    kafka_partition  INT            NOT NULL,
    kafka_offset     BIGINT         NOT NULL,
    message_key      VARCHAR(255)   NULL,
    payload          MEDIUMTEXT     NOT NULL,
    correlation_id   VARCHAR(64)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    failure_category VARCHAR(40)    NULL,
    failure_summary  VARCHAR(500)   NULL,
    delivery_count   INT            NOT NULL DEFAULT 1,
    received_at      DATETIME(6)    NOT NULL,
    processed_at     DATETIME(6)    NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_raw_messages_coordinates UNIQUE (topic, kafka_partition, kafka_offset),
    CONSTRAINT chk_raw_messages_status CHECK
        (status IN ('RECEIVED','PROCESSED','INVALID','PARSE_FAILED','DUPLICATE','FAILED')),
    CONSTRAINT chk_raw_messages_failure_category CHECK
        (failure_category IN ('MALFORMED_MESSAGE','UNSUPPORTED_MESSAGE_TYPE',
                              'FIELD_VALIDATION','RETRY_EXHAUSTED')),
    CONSTRAINT chk_raw_messages_delivery_count CHECK (delivery_count >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_raw_messages_status      ON raw_messages (status);
CREATE INDEX idx_raw_messages_received_at ON raw_messages (received_at);

-- One row per parsed AllocationInstruction. Created only when the business identity
-- (alloc_id, alloc_trans_type) parsed successfully; parse-level failures stay on
-- raw_messages. uq_allocations_business_key absorbs business duplicates (a resent
-- New/Replace/Cancel at a new offset) per ADR-0007. status is the persisted ADR-0006
-- state machine column; Phase 7 writes RECEIVED / INVALID, Phase 8 the rest.
CREATE TABLE allocations (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    alloc_id             VARCHAR(64)    NOT NULL,
    alloc_trans_type     VARCHAR(2)     NOT NULL,
    alloc_type           VARCHAR(3)     NULL,
    alloc_no_orders_type VARCHAR(2)     NULL,
    ref_alloc_id         VARCHAR(64)    NULL,
    status               VARCHAR(30)    NOT NULL,
    side                 VARCHAR(2)     NULL,
    quantity             DECIMAL(19,4)  NULL,
    avg_px               DECIMAL(19,8)  NULL,
    currency             VARCHAR(3)     NULL,
    trade_date           DATE           NULL,
    transact_time        DATETIME(6)    NULL,
    settl_type           VARCHAR(2)     NULL,
    settl_date           DATE           NULL,
    net_money            DECIMAL(19,4)  NULL,
    gross_trade_amt      DECIMAL(19,4)  NULL,
    symbol               VARCHAR(32)    NULL,
    security_id          VARCHAR(64)    NULL,
    security_id_source   VARCHAR(2)     NULL,
    security_exchange    VARCHAR(10)    NULL,
    raw_message_id       BIGINT         NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_allocations_business_key UNIQUE (alloc_id, alloc_trans_type),
    CONSTRAINT fk_allocations_raw_message
        FOREIGN KEY (raw_message_id) REFERENCES raw_messages (id),
    CONSTRAINT chk_allocations_status CHECK
        (status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                    'EXECUTIONS_RELEASED','COMPLETED','FAILED')),
    CONSTRAINT chk_allocations_trans_type CHECK (alloc_trans_type IN ('0','1','2'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_allocations_status         ON allocations (status);
CREATE INDEX idx_allocations_trade_date     ON allocations (trade_date);
CREATE INDEX idx_allocations_raw_message_id ON allocations (raw_message_id);

-- NoOrders repeating group, in wire order (seq). Values are stored as parsed; logical
-- validation against trading-service happens in Phase 8.
CREATE TABLE allocation_orders (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    allocation_id BIGINT         NOT NULL,
    seq           INT            NOT NULL,
    cl_ord_id     VARCHAR(64)    NULL,
    order_id      VARCHAR(64)    NULL,
    order_qty     DECIMAL(19,4)  NULL,
    order_avg_px  DECIMAL(19,8)  NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_allocation_orders_seq UNIQUE (allocation_id, seq),
    CONSTRAINT fk_allocation_orders_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- NoExecs repeating group, in wire order.
CREATE TABLE allocation_executions (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    allocation_id BIGINT         NOT NULL,
    seq           INT            NOT NULL,
    exec_id       VARCHAR(64)    NULL,
    last_qty      DECIMAL(19,4)  NULL,
    last_px       DECIMAL(19,8)  NULL,
    last_capacity VARCHAR(2)     NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_allocation_executions_seq UNIQUE (allocation_id, seq),
    CONSTRAINT fk_allocation_executions_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- NoAllocs repeating group (allocation accounts), in wire order.
CREATE TABLE allocation_accounts (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    allocation_id         BIGINT         NOT NULL,
    seq                   INT            NOT NULL,
    alloc_account         VARCHAR(64)    NULL,
    alloc_qty             DECIMAL(19,4)  NULL,
    alloc_price           DECIMAL(19,8)  NULL,
    alloc_avg_px          DECIMAL(19,8)  NULL,
    alloc_net_money       DECIMAL(19,4)  NULL,
    alloc_settl_currency  VARCHAR(3)     NULL,
    alloc_settl_curr_amt  DECIMAL(19,4)  NULL,
    alloc_text            VARCHAR(255)   NULL,
    created_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_allocation_accounts_seq UNIQUE (allocation_id, seq),
    CONSTRAINT fk_allocation_accounts_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- NoMiscFees nests inside NoAllocs in FIX 4.4 AllocationInstruction, so every fee row
-- belongs to an allocation account entry.
CREATE TABLE allocation_misc_fees (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    allocation_account_id BIGINT         NOT NULL,
    seq                   INT            NOT NULL,
    misc_fee_amt          DECIMAL(19,4)  NULL,
    misc_fee_curr         VARCHAR(3)     NULL,
    misc_fee_type         VARCHAR(3)     NULL,
    misc_fee_basis        VARCHAR(2)     NULL,
    created_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_allocation_misc_fees_seq UNIQUE (allocation_account_id, seq),
    CONSTRAINT fk_allocation_misc_fees_account
        FOREIGN KEY (allocation_account_id) REFERENCES allocation_accounts (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Collect-all validation output (ADR-0012): ALL errors for a message are stored, never
-- just the first. raw_message_id is always known; allocation_id only when the business
-- identity parsed.
CREATE TABLE validation_errors (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    raw_message_id BIGINT        NOT NULL,
    allocation_id  BIGINT        NULL,
    error_code     VARCHAR(40)   NOT NULL,
    message        VARCHAR(500)  NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_validation_errors_raw_message
        FOREIGN KEY (raw_message_id) REFERENCES raw_messages (id),
    CONSTRAINT fk_validation_errors_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id),
    CONSTRAINT chk_validation_errors_code CHECK
        (error_code IN ('MALFORMED_MESSAGE','UNSUPPORTED_MESSAGE_TYPE',
                        'MISSING_REQUIRED_FIELD','INVALID_VALUE_FORMAT',
                        'VALUE_NOT_SUPPORTED','VALUE_OUT_OF_BOUND','CONFLICTING_FIELDS'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_validation_errors_raw_message ON validation_errors (raw_message_id);
CREATE INDEX idx_validation_errors_allocation  ON validation_errors (allocation_id);

-- FIX fields implicated in a validation error (dictionary-resolved names).
CREATE TABLE validation_error_fields (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    validation_error_id BIGINT        NOT NULL,
    field_id            INT           NULL,
    field_name          VARCHAR(100)  NULL,
    field_value         VARCHAR(255)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_validation_error_fields_error
        FOREIGN KEY (validation_error_id) REFERENCES validation_errors (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox (ADR-0005): written in the same transaction as the state change
-- it describes; a scheduled poller publishes unpublished rows ordered by id and stamps
-- published_at. payload is the Avro binary encoding of the generated event class
-- (writer schema = the classpath schema for event_type).
CREATE TABLE outbox_event (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(36)   NOT NULL,
    event_type     VARCHAR(60)   NOT NULL,
    aggregate_type VARCHAR(40)   NOT NULL,
    aggregate_id   VARCHAR(64)   NOT NULL,
    topic          VARCHAR(255)  NOT NULL,
    message_key    VARCHAR(64)   NOT NULL,
    payload        MEDIUMBLOB    NOT NULL,
    correlation_id VARCHAR(64)   NOT NULL,
    causation_id   VARCHAR(64)   NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)  NULL,
    created_at     DATETIME(6)   NOT NULL,
    published_at   DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id),
    CONSTRAINT chk_outbox_event_attempts CHECK (attempts >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Poller scan: WHERE published_at IS NULL ORDER BY id.
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (published_at, id);
