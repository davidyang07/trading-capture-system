# Data Ownership & Persistence Standards

One MySQL 8.4 instance, four schemas, four users. Each schema is owned and migrated (Flyway) exclusively by its service. Planned core tables below are design targets; they materialize in Phases 3–8.

## Schemas and planned core tables

| Schema (user) | Planned tables (implementing phase) |
|---|---|
| `tcs_trading` (`trading_svc`) | `orders` (owner subject, status, side, type, price, qty, settle fields) · `executions` (immutable rows; `exec_ref_id`, resolved `root_execution_id`, exec type F/G/H, allocation state) · `execution_misc_fees` · `execution_errors` + `execution_error_fields` · seeds (3) |
| `tcs_account` (`account_svc`) | `accounts` (status, `@Version`) · `cash_transactions` · `security_transactions` (immutable ledgers) · balance views/queries by currency/instrument · seeds (4) |
| `tcs_enrichment` (`enrichment_svc`) | `instruments` · `instrument_alt_ids` · `exchanges` · `currencies` · `settlement_options` · `misc_fee_rules` · deterministic seeds (5) |
| `tcs_allocation` (`allocation_svc`) | `raw_messages` (unique `(topic,partition,offset)`, verbatim payload, parse status) · `allocations` (unique `(alloc_id, alloc_trans_type)`, state machine column) · `allocation_orders` · `allocation_executions` · `allocation_accounts` · `allocation_misc_fees` · `validation_errors` (+ fields) · `outbox_event` (7–8) |

## Type standards (mandatory, enforced by review + rules)

| Concern | Standard |
|---|---|
| Money, fees, balances, quantities | `BigDecimal` ↔ `DECIMAL(19,4)` |
| Prices, rates | `BigDecimal` ↔ `DECIMAL(19,8)` |
| Timestamps | `Instant` ↔ `DATETIME(6)`, always UTC: JDBC `connectionTimeZone=UTC`, `hibernate.jdbc.time_zone=UTC`; never `java.sql.Timestamp`/`ZonedDateTime` in entities |
| Enums | Explicit string codes via JPA `@Convert` converters (FIX code mappings preserved, e.g. ExecType F/G/H); never `ORDINAL` |
| Primary keys | Meaningful business IDs where the domain has them (`exec_<uuid>`, allocId); surrogate `BIGINT AUTO_INCREMENT` otherwise |
| Concurrency | `@Version` on mutable money-bearing aggregates (accounts) and on execution allocation state |
| Auditing | `created_at` / `updated_at` on all tables; execution and transaction rows are immutable (corrections append) |

## Migration rules

- Flyway versioned migrations `V<seq>__<description>.sql` per service; applied migrations are immutable — fix-forward only.
- Seed/demo data ships as separate, deterministic migrations (stable IDs) so integration tests and demos are reproducible.
- Schemas initialize from empty databases with no undocumented manual SQL; `ddl-auto` is `validate` (never `update`).

## Balance semantics (account-service)

Balances are ledger sums over immutable transaction rows (pattern from `spring-async-hw`, re-typed to `BigDecimal`):
`cash_balance(account, currency) = Σ credits − Σ debits`. Sufficient-funds/securities validation and negative-balance prevention are enforced in the service inside the same transaction that appends the ledger row, guarded by optimistic locking on the account row. Suspended accounts reject trading/allocation participation.

## Audit chains (trading-service)

Corrections and cancellations never mutate the original execution: a new row (`TRADE_CORRECT` / `TRADE_CANCEL`) references its target via `exec_ref_id`, and `root_execution_id` is resolved at write time by walking the chain (pattern from `execution-engine-answer`; the reference schema declared `root_id` but never populated it — this design fixes that). Chains remain fully queryable; invalid state transitions (e.g., correcting a cancelled execution) are rejected.

## What Redis may hold (never authoritative)

Enrichment cache entries (15-min TTL) and allocation dedup pre-filter keys (7-day TTL). Loss of Redis degrades performance, never correctness (ADR-0007).
