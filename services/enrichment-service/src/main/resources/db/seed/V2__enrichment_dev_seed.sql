-- Deterministic development/demo reference data for tcs_enrichment (Phase 5).
-- Lives in db/seed so production-style deployments can exclude it (spring.flyway.locations).
-- Stable IDs + fixed UTC timestamps so integration tests and demos are reproducible. The
-- instruments (AAPL, MSFT) align with the account-service seed positions; the paired XYZ
-- listings deliberately share an exchange symbol on two exchanges to exercise ambiguity.

INSERT INTO exchanges (mic, name, country, timezone, created_at, updated_at, version) VALUES
    ('XNAS', 'Nasdaq Stock Market',   'US', 'America/New_York', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('XNYS', 'New York Stock Exchange','US', 'America/New_York', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('XLON', 'London Stock Exchange',  'GB', 'Europe/London',    '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);

INSERT INTO currencies (code, name, minor_units, created_at, updated_at, version) VALUES
    ('USD', 'US Dollar',      2, '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('EUR', 'Euro',           2, '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('GBP', 'Pound Sterling', 2, '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('JPY', 'Japanese Yen',   0, '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);

INSERT INTO settlement_options (code, name, settlement_days, description, created_at, updated_at, version) VALUES
    ('CASH',     'Cash / same day', 0, 'Settles on trade date',        '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('NEXT_DAY', 'Next business day',1, 'Settles T+1',                  '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('T_PLUS_2', 'Regular way',     2, 'Standard equity settlement',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('T_PLUS_3', 'T+3',             3, 'Extended settlement',          '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);

INSERT INTO instruments (instrument_id, name, instrument_type, exchange_mic, currency_code, status, created_at, updated_at, version) VALUES
    ('AAPL',   'Apple Inc.',                'EQUITY', 'XNAS', 'USD', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('MSFT',   'Microsoft Corporation',     'EQUITY', 'XNAS', 'USD', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('SPY',    'SPDR S&P 500 ETF Trust',    'ETF',    'XNYS', 'USD', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('VOD.L',  'Vodafone Group plc',        'EQUITY', 'XLON', 'GBP', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('DELIST', 'Delisted Example Corp',     'EQUITY', 'XNAS', 'USD', 'INACTIVE', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('XYZ.OQ', 'XYZ Holdings (Nasdaq)',     'EQUITY', 'XNAS', 'USD', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('XYZ.L',  'XYZ Holdings (London)',     'EQUITY', 'XLON', 'GBP', 'ACTIVE',   '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);

INSERT INTO instrument_alt_ids (instrument_id, source, id_value, exchange_mic, created_at) VALUES
    ('AAPL',   'CUSIP',           '037833100',    'XNAS', '2026-07-01 00:00:00.000000'),
    ('AAPL',   'ISIN',            'US0378331005', 'XNAS', '2026-07-01 00:00:00.000000'),
    ('AAPL',   'EXCHANGE_SYMBOL', 'AAPL',         'XNAS', '2026-07-01 00:00:00.000000'),
    ('MSFT',   'CUSIP',           '594918104',    'XNAS', '2026-07-01 00:00:00.000000'),
    ('MSFT',   'ISIN',            'US5949181045', 'XNAS', '2026-07-01 00:00:00.000000'),
    ('MSFT',   'EXCHANGE_SYMBOL', 'MSFT',         'XNAS', '2026-07-01 00:00:00.000000'),
    ('SPY',    'EXCHANGE_SYMBOL', 'SPY',          'XNYS', '2026-07-01 00:00:00.000000'),
    ('VOD.L',  'SEDOL',           'BH4HKS3',      'XLON', '2026-07-01 00:00:00.000000'),
    ('VOD.L',  'EXCHANGE_SYMBOL', 'VOD',          'XLON', '2026-07-01 00:00:00.000000'),
    -- Same exchange symbol XYZ on two exchanges: ambiguous without an exchange filter.
    ('XYZ.OQ', 'EXCHANGE_SYMBOL', 'XYZ',          'XNAS', '2026-07-01 00:00:00.000000'),
    ('XYZ.L',  'EXCHANGE_SYMBOL', 'XYZ',          'XLON', '2026-07-01 00:00:00.000000');

INSERT INTO fee_rules (fee_rule_id, name, fee_type, basis, rate, currency_code, exchange_mic, instrument_type, status, created_at, updated_at, version) VALUES
    ('US-EQ-COMMISSION', 'US equity commission',   'COMMISSION',     'PERCENTAGE', 0.00100000, 'USD',  NULL,   'EQUITY', 'ACTIVE', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('US-SEC-FEE',       'US SEC regulatory fee',  'REGULATORY_FEE', 'PERCENTAGE', 0.00002780, 'USD',  NULL,   NULL,     'ACTIVE', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('LSE-EXCH-FEE',     'LSE exchange fee',       'EXCHANGE_FEE',   'PER_UNIT',   0.05000000, 'GBP',  'XLON', NULL,     'ACTIVE', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('GLOBAL-CLEARING',  'Flat clearing fee',      'CLEARING_FEE',   'ABSOLUTE',   1.00000000, 'USD',  NULL,   NULL,     'ACTIVE', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('LEGACY-TAX',       'Retired stamp duty',     'TAX',            'PERCENTAGE', 0.00500000, 'GBP',  'XLON', 'EQUITY', 'INACTIVE','2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);
