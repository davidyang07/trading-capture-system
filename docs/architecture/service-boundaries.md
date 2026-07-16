# Service Boundaries

Each service owns its domain, its MySQL schema, and its API contract. No service reads another's tables; all cross-domain access is via REST (documented in `contracts/openapi/`) or events (`docs/contracts/event-catalog.md`).

## trading-service (`:8081`, schema `tcs_trading`)

Owns: orders; executions; order↔execution relationships; execution search; **append-only** correction (Trade Correct) and cancellation (Trade Cancel) chains with resolved root references; cumulative/leaves quantity derivation; execution allocation state (set via machine API by allocation-engine); order eligibility rules (update/delete only NEW; duplicate order-ID rejection; generated IDs when absent); ownership enforcement (investor sees only owned orders).

Outbound calls: enrichment-service (instrument/identifier validation on create), account-service (account status check on order create). Inbound machine API: allocation-engine (execution lookup + allocation-state updates, scope `allocation:process`).

Explicitly not owned: balances (account-service), reference data (enrichment-service), allocation aggregates (allocation-engine).

## account-service (`:8082`, schema `tcs_account`)

Owns: financial accounts and lifecycle (create/activate/suspend); cash transactions and cash balances by currency; security transactions and security balances by instrument; balance history; sufficient-cash / sufficient-security validation; negative-balance prevention; suspended-account participation rules; optimistic locking on mutable aggregates; machine-facing batch lookup/validation API.

Accounting semantics (documented in data-ownership.md): balances are ledger sums over immutable transactions — deposits/credits add, withdrawals/debits subtract; transactions are never updated or deleted.

## enrichment-service (`:8083`, schema `tcs_enrichment`)

Owns: instruments; alternate instrument identifiers (CUSIP/ISIN/SEDOL/exchange symbol); exchanges (MIC); supported currencies; settlement reference data; miscellaneous fee rules (applied by consumers only when incoming data has no fees — non-intrusive rule). Deterministic Flyway seed data. Redis read-through caching with documented TTLs, eviction on admin mutation, and graceful degradation to MySQL when Redis is down.

## allocation-engine (`:8084`, schema `tcs_allocation`)

Owns: raw inbound FIX messages (preserved verbatim, unique per topic/partition/offset); QuickFIX/J parsing of AllocationInstruction (35=J) incl. repeating groups (orders, executions, allocation accounts, misc fees); collect-all field validation and persisted validation errors; allocation aggregates and the persisted processing state machine; smart execution selection; New/Replace/Cancel semantics (Replace/Cancel release previously allocated executions); idempotent consumption (DB-authoritative, Redis pre-filter); retry/DLT topology for transient failures; publication of all seven domain events via transactional outbox.

Outbound calls (M2M + Resilience4j): trading-service, account-service, enrichment-service. No public browser-facing REST API — Kafka-driven, plus actuator.

## admin-web (`:3000`)

Owns: UI workflows for orders/executions/accounts; OIDC login/callback/logout via Auth.js; the BFF proxy that attaches user tokens server-side; permission-aware navigation and actions (UX only — authorization is always enforced server-side by the services).

## Boundary rules

1. Database ownership is physical: each service's MySQL user is granted only its own schema (ADR-0013).
2. Persistence entities are never exposed as API contracts; DTOs per service (ADR-0014).
3. Events are owned by allocation-engine (single producer); schemas live only in `libs/event-contracts`.
4. Shared libraries (`observability-support`, `test-support`, `event-contracts`) contain no business logic.
5. New cross-service calls require an OpenAPI contract update in the same change.
