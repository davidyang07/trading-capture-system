-- Trading Capture System - tcs_trading schema (Phase 3).
-- Flyway is the only owner of this schema (ddl-auto=validate). The schema name itself
-- comes from the JDBC connection (tcs_trading in compose, container DB in tests), so no
-- statement below references a database name.
--
-- Type standards (docs/architecture/data-ownership.md / ADR-0014):
--   money & quantities DECIMAL(19,4) | prices & rates DECIMAL(19,8)
--   timestamps DATETIME(6) stored UTC | enums as explicit FIX string codes
--
-- Enum code domains (JPA converters own the mapping):
--   orders.status / executions.ord_status : '0'=NEW '1'=PARTIALLY_FILLED '2'=FILLED
--   side       : '1'=BUY '2'=SELL
--   order_type : '1'=MARKET '2'=LIMIT
--   price_type : '0'=PERCENTAGE '1'=PER_UNIT '2'=FIXED_AMOUNT
--   settle_type: '0'=REGULAR '1'=CASH '2'=NEXT_DAY '3'=T_PLUS_2 '4'=T_PLUS_3 '5'=T_PLUS_4
--   exec_type  : 'F'=TRADE 'G'=TRADE_CORRECT 'H'=TRADE_CANCEL
--   misc fee basis : '0'=ABSOLUTE '1'=PER_UNIT '2'=PERCENTAGE

CREATE TABLE orders (
    order_id        VARCHAR(64)    NOT NULL,
    cl_ord_id       VARCHAR(64)    NULL,
    owner_subject   VARCHAR(255)   NOT NULL,
    account_id      VARCHAR(64)    NOT NULL,
    instrument_id   VARCHAR(64)    NOT NULL,
    status          VARCHAR(2)     NOT NULL,
    side            VARCHAR(2)     NOT NULL,
    order_type      VARCHAR(2)     NOT NULL,
    price           DECIMAL(19,8)  NOT NULL,
    price_type      VARCHAR(2)     NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    quantity        DECIMAL(19,4)  NOT NULL,
    cum_qty         DECIMAL(19,4)  NOT NULL DEFAULT 0,
    leaves_qty      DECIMAL(19,4)  NOT NULL,
    settle_type     VARCHAR(2)     NOT NULL,
    settle_date     DATE           NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id),
    CONSTRAINT chk_orders_status        CHECK (status IN ('0','1','2')),
    CONSTRAINT chk_orders_side          CHECK (side IN ('1','2')),
    CONSTRAINT chk_orders_order_type    CHECK (order_type IN ('1','2')),
    CONSTRAINT chk_orders_price_type    CHECK (price_type IN ('0','1','2')),
    CONSTRAINT chk_orders_settle_type   CHECK (settle_type IN ('0','1','2','3','4','5')),
    CONSTRAINT chk_orders_quantity      CHECK (quantity > 0),
    CONSTRAINT chk_orders_price         CHECK (price > 0),
    CONSTRAINT chk_orders_cum_qty       CHECK (cum_qty >= 0 AND cum_qty <= quantity),
    CONSTRAINT chk_orders_leaves_qty    CHECK (leaves_qty >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_owner_subject ON orders (owner_subject);
CREATE INDEX idx_orders_status        ON orders (status);
CREATE INDEX idx_orders_instrument_id ON orders (instrument_id);
CREATE INDEX idx_orders_account_id    ON orders (account_id);
CREATE INDEX idx_orders_settle_date   ON orders (settle_date);
CREATE INDEX idx_orders_created_at    ON orders (created_at);

-- Executions are an append-only audit history:
--   * rows are never UPDATEd except the allocation-state pair (allocation_state,
--     allocation_id, updated_at, version) which is operational state, not trade history;
--   * a correction (G) or cancellation (H) references its target via exec_ref_id and the
--     start of its chain via root_execution_id (TRADE rows are their own root);
--   * uq_executions_exec_ref_id makes every execution amendable at most once, which
--     forbids chain branching and, together with the tip-only service rule, cycles.
CREATE TABLE executions (
    internal_id        VARCHAR(50)    NOT NULL,
    exec_id            VARCHAR(64)    NOT NULL,
    order_id           VARCHAR(64)    NOT NULL,
    exec_type          VARCHAR(1)     NOT NULL,
    exec_ref_id        VARCHAR(50)    NULL,
    root_execution_id  VARCHAR(50)    NOT NULL,
    ord_status         VARCHAR(2)     NOT NULL,
    side               VARCHAR(2)     NOT NULL,
    last_qty           DECIMAL(19,4)  NOT NULL,
    last_px            DECIMAL(19,8)  NULL,
    cum_qty            DECIMAL(19,4)  NOT NULL,
    leaves_qty         DECIMAL(19,4)  NOT NULL,
    avg_px             DECIMAL(19,8)  NULL,
    currency           VARCHAR(3)     NOT NULL,
    trade_date         DATE           NOT NULL,
    transact_time      DATETIME(6)    NOT NULL,
    settle_type        VARCHAR(2)     NOT NULL,
    settle_date        DATE           NOT NULL,
    text               VARCHAR(500)   NULL,
    allocation_state   VARCHAR(20)    NOT NULL DEFAULT 'UNALLOCATED',
    allocation_id      VARCHAR(64)    NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (internal_id),
    CONSTRAINT uq_executions_exec_id     UNIQUE (exec_id),
    CONSTRAINT uq_executions_exec_ref_id UNIQUE (exec_ref_id),
    CONSTRAINT fk_executions_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_executions_exec_ref
        FOREIGN KEY (exec_ref_id) REFERENCES executions (internal_id),
    CONSTRAINT fk_executions_root
        FOREIGN KEY (root_execution_id) REFERENCES executions (internal_id),
    CONSTRAINT chk_executions_exec_type CHECK (exec_type IN ('F','G','H')),
    CONSTRAINT chk_executions_ref_presence
        CHECK ((exec_type = 'F' AND exec_ref_id IS NULL)
            OR (exec_type IN ('G','H') AND exec_ref_id IS NOT NULL)),
    CONSTRAINT chk_executions_no_self_ref
        CHECK (exec_ref_id IS NULL OR exec_ref_id <> internal_id),
    CONSTRAINT chk_executions_ord_status CHECK (ord_status IN ('0','1','2')),
    CONSTRAINT chk_executions_side       CHECK (side IN ('1','2')),
    CONSTRAINT chk_executions_last_qty   CHECK (last_qty >= 0),
    CONSTRAINT chk_executions_last_px    CHECK (last_px IS NULL OR last_px > 0),
    CONSTRAINT chk_executions_cum_qty    CHECK (cum_qty >= 0),
    CONSTRAINT chk_executions_leaves_qty CHECK (leaves_qty >= 0),
    CONSTRAINT chk_executions_settle_type CHECK (settle_type IN ('0','1','2','3','4','5')),
    CONSTRAINT chk_executions_allocation_state
        CHECK (allocation_state IN ('UNALLOCATED','ALLOCATED')),
    CONSTRAINT chk_executions_allocation_pair
        CHECK ((allocation_state = 'ALLOCATED' AND allocation_id IS NOT NULL)
            OR (allocation_state = 'UNALLOCATED' AND allocation_id IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_executions_order_id         ON executions (order_id);
CREATE INDEX idx_executions_root             ON executions (root_execution_id);
CREATE INDEX idx_executions_exec_type        ON executions (exec_type);
CREATE INDEX idx_executions_transact_time    ON executions (transact_time);
CREATE INDEX idx_executions_trade_date       ON executions (trade_date);
CREATE INDEX idx_executions_allocation_state ON executions (allocation_state);
CREATE INDEX idx_executions_allocation_id    ON executions (allocation_id);

-- Fees belong to their (immutable) execution row; corrections restate fees on the new row.
CREATE TABLE execution_misc_fees (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    execution_internal_id  VARCHAR(50)    NOT NULL,
    fee_type               VARCHAR(20)    NOT NULL,
    amount                 DECIMAL(19,4)  NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    basis                  VARCHAR(1)     NOT NULL,
    created_at             DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_execution_misc_fees_execution
        FOREIGN KEY (execution_internal_id) REFERENCES executions (internal_id),
    CONSTRAINT chk_execution_misc_fees_amount CHECK (amount >= 0),
    CONSTRAINT chk_execution_misc_fees_basis  CHECK (basis IN ('0','1','2'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_execution_misc_fees_execution ON execution_misc_fees (execution_internal_id);
