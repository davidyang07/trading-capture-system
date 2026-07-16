-- Deterministic development/demo seed for tcs_trading (Phase 3).
-- Ships in db/seed, included via spring.flyway.locations in development configuration and
-- excluded wherever a clean schema is required. Stable IDs and fixed UTC timestamps keep
-- demos and integration assertions reproducible.
--
-- Owner subjects are the Keycloak dev-realm usernames (preferred_username claim):
-- investor1, investor2, tcs-admin. Aggregates (status, cum_qty, leaves_qty, avg_px and the
-- per-row snapshots) are pre-computed to be exactly what the service would derive.

INSERT INTO orders
    (order_id, cl_ord_id, owner_subject, account_id, instrument_id,
     status, side, order_type, price, price_type, currency,
     quantity, cum_qty, leaves_qty, settle_type, settle_date,
     created_at, updated_at, version)
VALUES
    -- Filled by a single trade (chain: exec_seed_001).
    ('ORD-SEED-001', 'clord-seed-001', 'investor1', 'ACC-SEED-INV1', 'AAPL',
     '2', '1', '2', 189.25000000, '1', 'USD',
     100.0000, 100.0000, 0.0000, '3', '2026-07-03',
     '2026-07-01 09:00:00.000000', '2026-07-01 09:15:00.000000', 0),

    -- Partially filled by two trades (exec_seed_002, exec_seed_003).
    ('ORD-SEED-002', 'clord-seed-002', 'investor1', 'ACC-SEED-INV1', 'MSFT',
     '1', '1', '2', 415.50000000, '1', 'USD',
     200.0000, 120.0000, 80.0000, '3', '2026-07-03',
     '2026-07-01 09:05:00.000000', '2026-07-01 09:40:00.000000', 0),

    -- Filled, then price-corrected: chain exec_seed_004 (F) -> exec_seed_005 (G).
    ('ORD-SEED-003', 'clord-seed-003', 'investor2', 'ACC-SEED-INV2', 'TSLA',
     '2', '2', '2', 240.50000000, '1', 'USD',
     150.0000, 150.0000, 0.0000, '0', '2026-07-02',
     '2026-07-01 09:10:00.000000', '2026-07-01 10:20:00.000000', 0),

    -- Trade then cancelled: chain exec_seed_006 (F) -> exec_seed_007 (H).
    -- Back to NEW with execution history: not deletable, still updatable-by-rule? No —
    -- update remains allowed only while NEW; deletion is blocked by the execution rows.
    ('ORD-SEED-004', 'clord-seed-004', 'investor2', 'ACC-SEED-INV2', 'AMZN',
     '0', '1', '2', 178.60000000, '1', 'USD',
     300.0000, 0.0000, 300.0000, '3', '2026-07-06',
     '2026-07-01 09:20:00.000000', '2026-07-01 11:05:00.000000', 0),

    -- Untouched NEW market order owned by the admin (safe update/delete demo target).
    ('ORD-SEED-005', 'clord-seed-005', 'tcs-admin', 'ACC-SEED-ADMIN', 'GOOG',
     '0', '1', '1', 180.35000000, '1', 'USD',
     500.0000, 0.0000, 500.0000, '0', '2026-07-06',
     '2026-07-01 09:25:00.000000', '2026-07-01 09:25:00.000000', 0),

    -- Partially filled with the fill already allocated (ALLOC-SEED-001):
    -- demonstrates the allocated-execution amendment guard and the release flow.
    ('ORD-SEED-006', 'clord-seed-006', 'investor1', 'ACC-SEED-INV1', 'AAPL',
     '1', '2', '2', 190.10000000, '1', 'USD',
     250.0000, 100.0000, 150.0000, '3', '2026-07-03',
     '2026-07-01 09:30:00.000000', '2026-07-01 10:45:00.000000', 0);

INSERT INTO executions
    (internal_id, exec_id, order_id, exec_type, exec_ref_id, root_execution_id,
     ord_status, side, last_qty, last_px, cum_qty, leaves_qty, avg_px,
     currency, trade_date, transact_time, settle_type, settle_date, text,
     allocation_state, allocation_id, created_at, updated_at, version)
VALUES
    ('exec_seed_001', 'EXEC-SEED-001', 'ORD-SEED-001', 'F', NULL, 'exec_seed_001',
     '2', '1', 100.0000, 189.20000000, 100.0000, 0.0000, 189.20000000,
     'USD', '2026-07-01', '2026-07-01 09:15:00.000000', '3', '2026-07-03', 'Seed fill',
     'UNALLOCATED', NULL, '2026-07-01 09:15:00.000000', '2026-07-01 09:15:00.000000', 0),

    ('exec_seed_002', 'EXEC-SEED-002', 'ORD-SEED-002', 'F', NULL, 'exec_seed_002',
     '1', '1', 80.0000, 415.10000000, 80.0000, 120.0000, 415.10000000,
     'USD', '2026-07-01', '2026-07-01 09:35:00.000000', '3', '2026-07-03', 'Seed partial fill 1/2',
     'UNALLOCATED', NULL, '2026-07-01 09:35:00.000000', '2026-07-01 09:35:00.000000', 0),

    ('exec_seed_003', 'EXEC-SEED-003', 'ORD-SEED-002', 'F', NULL, 'exec_seed_003',
     '1', '1', 40.0000, 415.35000000, 120.0000, 80.0000, 415.18333333,
     'USD', '2026-07-01', '2026-07-01 09:40:00.000000', '3', '2026-07-03', 'Seed partial fill 2/2',
     'UNALLOCATED', NULL, '2026-07-01 09:40:00.000000', '2026-07-01 09:40:00.000000', 0),

    ('exec_seed_004', 'EXEC-SEED-004', 'ORD-SEED-003', 'F', NULL, 'exec_seed_004',
     '2', '2', 150.0000, 240.00000000, 150.0000, 0.0000, 240.00000000,
     'USD', '2026-07-01', '2026-07-01 09:45:00.000000', '0', '2026-07-02', 'Seed fill (later corrected)',
     'UNALLOCATED', NULL, '2026-07-01 09:45:00.000000', '2026-07-01 09:45:00.000000', 0),

    ('exec_seed_005', 'EXEC-SEED-005', 'ORD-SEED-003', 'G', 'exec_seed_004', 'exec_seed_004',
     '2', '2', 150.0000, 241.00000000, 150.0000, 0.0000, 241.00000000,
     'USD', '2026-07-01', '2026-07-01 10:20:00.000000', '0', '2026-07-02', 'Seed price correction',
     'UNALLOCATED', NULL, '2026-07-01 10:20:00.000000', '2026-07-01 10:20:00.000000', 0),

    ('exec_seed_006', 'EXEC-SEED-006', 'ORD-SEED-004', 'F', NULL, 'exec_seed_006',
     '1', '1', 100.0000, 178.55000000, 100.0000, 200.0000, 178.55000000,
     'USD', '2026-07-01', '2026-07-01 10:00:00.000000', '3', '2026-07-06', 'Seed fill (later cancelled)',
     'UNALLOCATED', NULL, '2026-07-01 10:00:00.000000', '2026-07-01 10:00:00.000000', 0),

    ('exec_seed_007', 'EXEC-SEED-007', 'ORD-SEED-004', 'H', 'exec_seed_006', 'exec_seed_006',
     '0', '1', 0.0000, NULL, 0.0000, 300.0000, NULL,
     'USD', '2026-07-01', '2026-07-01 11:05:00.000000', '3', '2026-07-06', 'Seed cancellation',
     'UNALLOCATED', NULL, '2026-07-01 11:05:00.000000', '2026-07-01 11:05:00.000000', 0),

    ('exec_seed_008', 'EXEC-SEED-008', 'ORD-SEED-006', 'F', NULL, 'exec_seed_008',
     '1', '2', 100.0000, 190.00000000, 100.0000, 150.0000, 190.00000000,
     'USD', '2026-07-01', '2026-07-01 10:45:00.000000', '3', '2026-07-03', 'Seed allocated fill',
     'ALLOCATED', 'ALLOC-SEED-001', '2026-07-01 10:45:00.000000', '2026-07-01 10:45:00.000000', 0);

INSERT INTO execution_misc_fees
    (execution_internal_id, fee_type, amount, currency, basis, created_at)
VALUES
    ('exec_seed_001', 'REGULATORY', 0.0400, 'USD', '0', '2026-07-01 09:15:00.000000'),
    ('exec_seed_004', 'EXCHANGE',   1.2500, 'USD', '0', '2026-07-01 09:45:00.000000');
