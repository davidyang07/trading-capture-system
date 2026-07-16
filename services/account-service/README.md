# account-service

Owns financial accounts, immutable cash/security ledgers, and ledger-derived balances
(`tcs_account` schema, port 8082). See `contracts/openapi/account-service.openapi.yaml` for
the API contract of record and `docs/adr/ADR-0002` for the service-boundary decision.

## Domain rules

**Accounts**
- The owner subject is derived from the verified JWT (`preferred_username`, falling back to
  `sub`) — never from the request body. Only an ADMIN may nominate a different `ownerSubject`
  at creation; an investor attempting it is rejected (400).
- Lifecycle: `PENDING → ACTIVE → SUSPENDED ⇄ ACTIVE`, any of `{PENDING, ACTIVE, SUSPENDED} → CLOSED`;
  `CLOSED` is terminal. Illegal transitions are 409 (`INVALID_STATUS_TRANSITION`).
- Closing requires every ledger-derived balance and position to be zero (`ACCOUNT_NOT_EMPTY`).
- Only `ACTIVE` accounts accept ledger posts (`ACCOUNT_NOT_ACTIVE`).
- `@Version` optimistic locking; duplicate client-supplied account IDs are rejected (409),
  missing IDs are generated (`ACC-<uuid>`).

**Ledgers (append-only)**
- A stored balance is never the source of truth. `cash_balance(account, currency) = Σ CREDIT − Σ DEBIT`
  and `position(account, instrument) = Σ RECEIPT − Σ DELIVERY`, summed over immutable rows.
- Amounts and quantities are always strictly positive (`BigDecimal`, `DECIMAL(19,4)`); the
  direction carries the sign. Non-positive values are rejected at the DTO and by a DB CHECK.
- A DEBIT may not drive a cash balance negative (`INSUFFICIENT_FUNDS`); a DELIVERY may not
  exceed the held position (`INSUFFICIENT_SECURITIES`).
- Concurrency is serialized on the account: a ledger post loads the account under an optimistic
  **force-increment** lock, so two simultaneous posts cannot both commit — the loser gets 409
  (`CONCURRENT_MODIFICATION`), preventing double-spend without pessimistic locks.
- Machine-originated posts may carry an `idempotencyKey` (unique per account): a replay returns
  the original row with **200** (rather than a duplicate + 201); the DB unique constraint is the
  authority under races.

**Machine batch validation (for allocation-engine / trading-service)**
- `POST /internal/v1/accounts/batch` returns an independent verdict per item (existence, status,
  optional cash sufficiency, optional security sufficiency) — one bad account never fails the
  batch. Read-only and side-effect-free; cross-owner (no investor masking); requires a service
  scope (`account:balance` or `allocation:process`).

## Authorization matrix

| Endpoint | Scope | Role/ownership |
|---|---|---|
| `POST /api/v1/accounts` | `account:admin` | any holder; owner = JWT subject (ADMIN may set another) |
| `GET /api/v1/accounts`, `GET /api/v1/accounts/{id}` | `account:balance` | ADMIN: all; INVESTOR: own only |
| `PUT /api/v1/accounts/{id}` | `account:admin` | owner or ADMIN |
| `POST /api/v1/accounts/{id}/activate|suspend|close` | `account:admin` | owner or ADMIN |
| `GET /api/v1/accounts/{id}/balances` | `account:balance` | owner or ADMIN |
| `POST\|GET /api/v1/accounts/{id}/cash-transactions` | `account:balance` | owner or ADMIN |
| `POST\|GET /api/v1/accounts/{id}/security-transactions` | `account:balance` | owner or ADMIN |
| `POST /internal/v1/accounts/batch` | `account:balance` **or** `allocation:process` | machine (cross-owner) |

Unowned resources answer **404** (existence is never revealed); missing/invalid tokens 401 and
missing scope/role 403, all in the same RFC 7807 problem shape with `errorCode` and
`correlationId`. Tokens must carry issuer `…/realms/tcs` **and** audience `account-service`
(attached by the realm's `account-service-aud` client scope).

Public paths (documented): `/actuator/health*`, `/actuator/info`, `/v3/api-docs*`, `/swagger-ui*`.

## Persistence

Flyway owns the schema (`ddl-auto=validate`): `V1__account_schema.sql` (tables accounts /
cash_transactions / security_transactions, enum string-code columns with CHECK constraints,
`DECIMAL(19,4)` money/quantities, `DATETIME(6)` UTC, per-account unique idempotency keys,
deliberate index/constraint names) and `db/seed/V2__account_dev_seed.sql` (deterministic demo
data: 5 accounts incl. PENDING and SUSPENDED, opening deposits, and held positions; owners are
the Keycloak dev usernames; `ACC-SEED-INV1` matches the trading-service seed).

`spring.flyway.locations` defaults to `db/migration,db/seed`; production-style deployments
override it to `classpath:db/migration` only.

## Running locally

```powershell
./scripts/dev-up.ps1                                   # MySQL (host port 3307), Keycloak, Redis
./mvnw -pl services/account-service spring-boot:run    # http://localhost:8082/swagger-ui.html
```

## Tests

```powershell
./mvnw -B -ntp -pl services/account-service -am test                                  # full suite
./mvnw -B -ntp -pl services/account-service -am test "-DexcludedGroups=integration"   # fast loop, no Docker
```

Integration tests (tagged `integration`) use a singleton Testcontainers MySQL 8.4 and RS256
test tokens validated by the production issuer+audience validator chain.

## Deferred integrations (documented boundaries)

- No account domain events yet — the transactional outbox and event contracts arrive with
  Phases 6–8 (ADR-0005).
- `/actuator/prometheus` exposure and dashboards arrive in Phase 11 (ADR-0015); domain counters
  (`tcs.account.accounts.*`, `tcs.account.cash.*`, `tcs.account.security.*`,
  `tcs.account.idempotent.replays`) are registered now.
- The `account-service-aud` realm client scope is import-validated only until the dev Keycloak
  volume is re-imported (see repo `docs/status/known-gaps.md`).
