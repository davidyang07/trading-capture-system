-- Trading Capture System - tcs_enrichment schema (Phase 5).
-- Flyway is the only owner of this schema (ddl-auto=validate). The schema name comes from the
-- JDBC connection (tcs_enrichment in compose, container DB in tests), so no statement below
-- references a database name.
--
-- Type standards (docs/architecture/data-ownership.md / ADR-0014):
--   rates DECIMAL(19,8) | timestamps DATETIME(6) UTC | enums as explicit string codes.
--
-- Enum code domains (JPA converters own the mapping):
--   instruments.instrument_type / fee_rules.instrument_type : EQUITY|BOND|ETF|FUND
--   instruments.status / fee_rules.status                   : ACTIVE|INACTIVE
--   instrument_alt_ids.source                               : CUSIP|ISIN|SEDOL|EXCHANGE_SYMBOL
--   fee_rules.fee_type                                      : COMMISSION|EXCHANGE_FEE|REGULATORY_FEE|CLEARING_FEE|TAX
--   fee_rules.basis                                         : ABSOLUTE|PER_UNIT|PERCENTAGE

CREATE TABLE exchanges (
    mic         VARCHAR(4)   NOT NULL,
    name        VARCHAR(255) NOT NULL,
    country     VARCHAR(2)   NOT NULL,
    timezone    VARCHAR(64)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (mic)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE currencies (
    code        VARCHAR(3)   NOT NULL,
    name        VARCHAR(255) NOT NULL,
    minor_units INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (code),
    CONSTRAINT chk_currencies_minor_units CHECK (minor_units >= 0 AND minor_units <= 8)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE settlement_options (
    code            VARCHAR(16)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    settlement_days INT          NOT NULL,
    description     VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (code),
    CONSTRAINT chk_settlement_days CHECK (settlement_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE instruments (
    instrument_id   VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    instrument_type VARCHAR(20)  NOT NULL,
    exchange_mic    VARCHAR(4)   NOT NULL,
    currency_code   VARCHAR(3)   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_instruments_exchange FOREIGN KEY (exchange_mic) REFERENCES exchanges (mic),
    CONSTRAINT fk_instruments_currency FOREIGN KEY (currency_code) REFERENCES currencies (code),
    CONSTRAINT chk_instruments_type   CHECK (instrument_type IN ('EQUITY','BOND','ETF','FUND')),
    CONSTRAINT chk_instruments_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_instruments_exchange   ON instruments (exchange_mic);
CREATE INDEX idx_instruments_currency   ON instruments (currency_code);
CREATE INDEX idx_instruments_type       ON instruments (instrument_type);
CREATE INDEX idx_instruments_status     ON instruments (status);
CREATE INDEX idx_instruments_created_at ON instruments (created_at);

-- Alternate identifiers. exchange_mic is copied from the owning instrument so the uniqueness
-- constraint can enforce per-exchange uniqueness (an EXCHANGE_SYMBOL is unique only within an
-- exchange). Global uniqueness for CUSIP/ISIN/SEDOL is additionally enforced in the service.
CREATE TABLE instrument_alt_ids (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    instrument_id VARCHAR(64) NOT NULL,
    source        VARCHAR(20) NOT NULL,
    id_value      VARCHAR(64) NOT NULL,
    exchange_mic  VARCHAR(4)  NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_alt_ids_source_value_exchange UNIQUE (source, id_value, exchange_mic),
    CONSTRAINT fk_alt_ids_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (instrument_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_alt_ids_source CHECK (source IN ('CUSIP','ISIN','SEDOL','EXCHANGE_SYMBOL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_alt_ids_instrument    ON instrument_alt_ids (instrument_id);
CREATE INDEX idx_alt_ids_source_value  ON instrument_alt_ids (source, id_value);

CREATE TABLE fee_rules (
    fee_rule_id     VARCHAR(64)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    fee_type        VARCHAR(20)   NOT NULL,
    basis           VARCHAR(20)   NOT NULL,
    rate            DECIMAL(19,8) NOT NULL,
    currency_code   VARCHAR(3)    NULL,
    exchange_mic    VARCHAR(4)    NULL,
    instrument_type VARCHAR(20)   NULL,
    status          VARCHAR(20)   NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (fee_rule_id),
    CONSTRAINT fk_fee_rules_currency FOREIGN KEY (currency_code) REFERENCES currencies (code),
    CONSTRAINT fk_fee_rules_exchange FOREIGN KEY (exchange_mic) REFERENCES exchanges (mic),
    CONSTRAINT chk_fee_rules_type   CHECK (fee_type IN ('COMMISSION','EXCHANGE_FEE','REGULATORY_FEE','CLEARING_FEE','TAX')),
    CONSTRAINT chk_fee_rules_basis  CHECK (basis IN ('ABSOLUTE','PER_UNIT','PERCENTAGE')),
    CONSTRAINT chk_fee_rules_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_fee_rules_itype  CHECK (instrument_type IS NULL OR instrument_type IN ('EQUITY','BOND','ETF','FUND')),
    CONSTRAINT chk_fee_rules_rate   CHECK (rate >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_fee_rules_status     ON fee_rules (status);
CREATE INDEX idx_fee_rules_exchange   ON fee_rules (exchange_mic);
CREATE INDEX idx_fee_rules_type       ON fee_rules (instrument_type);
CREATE INDEX idx_fee_rules_currency   ON fee_rules (currency_code);
