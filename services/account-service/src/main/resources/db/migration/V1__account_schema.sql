-- Trading Capture System - tcs_account schema (Phase 4).
-- Flyway is the only owner of this schema (ddl-auto=validate). The schema name itself comes
-- from the JDBC connection (tcs_account in compose, container DB in tests), so no statement
-- below references a database name.
--
-- Type standards (docs/architecture/data-ownership.md / ADR-0014):
--   money, quantities & balances DECIMAL(19,4) | timestamps DATETIME(6) stored UTC
--   enums as explicit string codes (JPA converters own the mapping)
--
-- Balances are never stored: cash_balance(account,currency) = Σ CREDIT − Σ DEBIT and
-- position(account,instrument) = Σ RECEIPT − Σ DELIVERY are summed from the immutable ledger
-- rows below. The account row's version column serializes concurrent posts (ADR-0002).

CREATE TABLE accounts (
    account_id      VARCHAR(64)   NOT NULL,
    owner_subject   VARCHAR(255)  NOT NULL,
    account_name    VARCHAR(255)  NOT NULL,
    account_type    VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    base_currency   VARCHAR(3)    NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    CONSTRAINT chk_accounts_type   CHECK (account_type IN ('CASH','MARGIN')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_accounts_owner_subject ON accounts (owner_subject);
CREATE INDEX idx_accounts_status        ON accounts (status);
CREATE INDEX idx_accounts_type          ON accounts (account_type);
CREATE INDEX idx_accounts_base_currency ON accounts (base_currency);
CREATE INDEX idx_accounts_created_at    ON accounts (created_at);

-- Immutable cash ledger. Amounts are strictly positive; direction carries the sign.
-- idempotency_key is unique per account when present (MySQL allows multiple NULLs), so a
-- machine may safely retry a post; a non-null key seen before is recognized as a replay.
CREATE TABLE cash_transactions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_ref   VARCHAR(64)   NOT NULL,
    account_id        VARCHAR(64)   NOT NULL,
    direction         VARCHAR(10)   NOT NULL,
    transaction_type  VARCHAR(20)   NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    external_ref      VARCHAR(64)   NULL,
    idempotency_key   VARCHAR(80)   NULL,
    description       VARCHAR(500)  NULL,
    posted_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_cash_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT uq_cash_idempotency     UNIQUE (account_id, idempotency_key),
    CONSTRAINT fk_cash_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_cash_direction CHECK (direction IN ('CREDIT','DEBIT')),
    CONSTRAINT chk_cash_type      CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL','TRADE_SETTLEMENT','FEE','ADJUSTMENT')),
    CONSTRAINT chk_cash_amount    CHECK (amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_cash_account          ON cash_transactions (account_id);
CREATE INDEX idx_cash_account_currency ON cash_transactions (account_id, currency);
CREATE INDEX idx_cash_posted_at        ON cash_transactions (posted_at);
CREATE INDEX idx_cash_external_ref     ON cash_transactions (external_ref);

-- Immutable security ledger. Quantities are strictly positive; direction carries the sign.
CREATE TABLE security_transactions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_ref   VARCHAR(64)   NOT NULL,
    account_id        VARCHAR(64)   NOT NULL,
    direction         VARCHAR(10)   NOT NULL,
    transaction_type  VARCHAR(20)   NOT NULL,
    instrument_id     VARCHAR(64)   NOT NULL,
    quantity          DECIMAL(19,4) NOT NULL,
    external_ref      VARCHAR(64)   NULL,
    idempotency_key   VARCHAR(80)   NULL,
    description       VARCHAR(500)  NULL,
    posted_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_security_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT uq_security_idempotency     UNIQUE (account_id, idempotency_key),
    CONSTRAINT fk_security_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_security_direction CHECK (direction IN ('RECEIPT','DELIVERY')),
    CONSTRAINT chk_security_type      CHECK (transaction_type IN ('TRADE_SETTLEMENT','TRANSFER_IN','TRANSFER_OUT','ADJUSTMENT')),
    CONSTRAINT chk_security_quantity  CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_security_account            ON security_transactions (account_id);
CREATE INDEX idx_security_account_instrument ON security_transactions (account_id, instrument_id);
CREATE INDEX idx_security_posted_at          ON security_transactions (posted_at);
CREATE INDEX idx_security_external_ref       ON security_transactions (external_ref);
