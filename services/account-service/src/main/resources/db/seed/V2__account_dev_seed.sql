-- Deterministic development/demo seed for tcs_account (Phase 4).
-- Lives in db/seed so production-style deployments can exclude it (spring.flyway.locations).
-- Stable IDs + fixed UTC timestamps so integration tests and demos are reproducible.
-- Owners are the Keycloak dev-realm usernames (tcs-admin, investor1, investor2); account IDs
-- match the trading-service seed/example data (ACC-SEED-INV1 is used by seeded orders).

INSERT INTO accounts
    (account_id, owner_subject, account_name, account_type, status, base_currency, created_at, updated_at, version)
VALUES
    ('ACC-SEED-INV1',    'investor1', 'Investor One Cash',    'CASH',   'ACTIVE',    'USD', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('ACC-SEED-INV2',    'investor2', 'Investor Two Cash',    'CASH',   'ACTIVE',    'USD', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('ACC-SEED-ADMIN',   'tcs-admin', 'House Margin Account', 'MARGIN', 'ACTIVE',    'USD', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('ACC-SEED-PENDING', 'investor1', 'Investor One EUR',     'CASH',   'PENDING',   'EUR', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0),
    ('ACC-SEED-SUSPENDED','investor2','Investor Two Suspended','CASH',  'SUSPENDED', 'USD', '2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000', 0);

-- Cash ledger: opening deposits give the active accounts a positive balance.
INSERT INTO cash_transactions
    (transaction_ref, account_id, direction, transaction_type, amount, currency, external_ref, idempotency_key, description, posted_at)
VALUES
    ('CT-SEED-1', 'ACC-SEED-INV1',  'CREDIT', 'DEPOSIT', 100000.0000, 'USD', NULL, NULL, 'Opening deposit', '2026-07-01 00:00:00.000000'),
    ('CT-SEED-2', 'ACC-SEED-INV2',  'CREDIT', 'DEPOSIT',  50000.0000, 'USD', NULL, NULL, 'Opening deposit', '2026-07-01 00:00:00.000000'),
    ('CT-SEED-3', 'ACC-SEED-ADMIN', 'CREDIT', 'DEPOSIT', 250000.0000, 'USD', NULL, NULL, 'Opening deposit', '2026-07-01 00:00:00.000000'),
    ('CT-SEED-4', 'ACC-SEED-INV1',  'DEBIT',  'WITHDRAWAL', 5000.0000, 'USD', NULL, NULL, 'Cash withdrawal', '2026-07-02 00:00:00.000000');

-- Security ledger: a couple of held positions for demos and the sufficient-security path.
INSERT INTO security_transactions
    (transaction_ref, account_id, direction, transaction_type, instrument_id, quantity, external_ref, idempotency_key, description, posted_at)
VALUES
    ('ST-SEED-1', 'ACC-SEED-INV1', 'RECEIPT', 'TRADE_SETTLEMENT', 'AAPL', 500.0000, NULL, NULL, 'Settled buy', '2026-07-01 00:00:00.000000'),
    ('ST-SEED-2', 'ACC-SEED-INV2', 'RECEIPT', 'TRADE_SETTLEMENT', 'MSFT', 200.0000, NULL, NULL, 'Settled buy', '2026-07-01 00:00:00.000000'),
    ('ST-SEED-3', 'ACC-SEED-INV1', 'DELIVERY','TRADE_SETTLEMENT', 'AAPL', 100.0000, NULL, NULL, 'Settled sell', '2026-07-02 00:00:00.000000');
