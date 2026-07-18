# ADR-0002: account-service is a separate service

Status: Accepted (2026-07-14)

## Context
Account balances are a ledger domain: immutable transaction rows, computed balances, sufficient-funds validation, and concurrent mutation under optimistic locking. This is naturally modeled as its own service with a batch machine API.

## Decision
`account-service` owns accounts, cash/security transactions, and balances in `tcs_account`, exposing user-facing APIs and a machine-facing batch validation/lookup API consumed by allocation-engine and trading-service.

## Consequences
- Different consistency profile (locking + ledger append) evolves independently of trade capture.
- Allocation checks (insufficient cash/securities, suspended account) have one authoritative owner.
- Cross-service calls from trading/allocation are needed — accepted, with timeouts/resilience at the callers.
