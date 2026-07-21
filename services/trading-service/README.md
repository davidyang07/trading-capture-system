# trading-service

Owns orders, executions, and the append-only correction/cancellation audit chains
(`tcs_trading` schema, port 8081). See `contracts/openapi/trading-service.openapi.yaml`
for the API contract of record; the domain and ownership rules are summarized below.

## Domain rules

**Orders**
- The owner subject is derived from the verified JWT (`preferred_username`, falling back
  to `sub`) — never from the request body.
- `status`, `cumQty`, `leavesQty` are server-derived from the order's active executions;
  status is a pure function of cumQty (0 → NEW, = quantity → FILLED, else PARTIALLY_FILLED).
- Update and delete require status NEW. Delete additionally requires that no execution row
  references the order — an order whose fills were all cancelled is NEW again but keeps its
  audit history and therefore stays undeletable (backed by the DB `ON DELETE RESTRICT` FK).
- Duplicate client-supplied order IDs are rejected (409); missing IDs are generated
  (`ORD-<uuid>`).

**Executions (append-only)**
- A TRADE (`F`) starts a chain. TRADE_CORRECT (`G`) / TRADE_CANCEL (`H`) never mutate their
  target: they append a new row referencing it via `exec_ref_id`, with `root_execution_id`
  resolved at write time (the target's root, or the target itself). TRADE rows are their own
  root, so `root_execution_id` is never null.
- Only a chain's tip may be amended; superseded rows and cancellations are immutable targets
  (409). The DB unique constraint on `exec_ref_id` forbids branches and, with the tip rule,
  cycles.
- A cancellation voids its entire chain's contribution; freed quantity may be refilled.
- `cumQty`/`leavesQty`/`avgPx` and the order status are recomputed from the full active chain
  state inside the same transaction — client-supplied aggregate values do not exist in the
  contract. Overfills are rejected (409) before any state changes.
- The order row carries `@Version`: concurrent captures cannot both commit (the loser gets
  409 `CONCURRENT_MODIFICATION`).

**Allocation state (machine API for the Phase 7/8 allocation-engine)**
- Two-state machine per execution: `UNALLOCATED ⇄ ALLOCATED` (+ `allocation_id`), mutated
  only via `PUT /internal/v1/executions/allocation-state` (scope `allocation:process`).
- ALLOCATE and RELEASE are idempotent: replays return 200 with `appliedCount: 0`; binding an
  execution to a different allocation is a 409 conflict. Cancelled/superseded executions are
  ineligible.
- Allocated executions cannot be corrected or cancelled until released.

## Authorization matrix

| Endpoint | Scope | Role/ownership |
|---|---|---|
| `POST /api/v1/orders` | `order:create` | any authenticated user; owner = JWT subject |
| `GET /api/v1/orders`, `GET /api/v1/orders/{id}` | `order:read` | ADMIN/machine: all; INVESTOR: own only |
| `PUT /api/v1/orders/{id}` | `order:update` | owner or ADMIN; NEW only |
| `DELETE /api/v1/orders/{id}` | `order:delete` | owner or ADMIN; NEW + no executions |
| `POST /api/v1/executions`, `/correct`, `/cancel` | `execution:write` | **+ role ADMIN** (operational function) |
| `GET /api/v1/executions`, `/{id}` | `execution:read` | ADMIN/machine: all; INVESTOR: own orders only |
| `PUT /internal/v1/executions/allocation-state` | `allocation:process` | machine clients only |
| `POST /internal/v1/executions/resolve` | `allocation:process` | machine clients only |

The Phase 8 `/internal/v1/executions/resolve` endpoint resolves external FIX ExecIDs (tag 17) to
internal executions plus an allocation-eligibility verdict (`ELIGIBLE`, `NOT_FOUND`, `CANCELLED`,
`SUPERSEDED`, `ALREADY_ALLOCATED`), so the allocation-engine can bind by internal id. It is
read-only and per-item (an unknown id is a `NOT_FOUND` item, never a batch failure), and exposes
only the fields allocation selection/validation need — no full execution disclosure.

Unowned resources answer **404** (existence is never revealed); missing/invalid tokens 401
and missing scope/role 403, all in the same RFC 7807 problem shape with `errorCode` and
`correlationId`. Tokens must carry issuer `…/realms/tcs` **and** audience `trading-service`
(attached by the realm's `trading-service-aud` client scope). The enforced per-endpoint
authorization matrix is in `SECURITY.md`; `LiveKeycloakAuthorizationTest`
proves it against a real Keycloak container with real issued tokens (Phase 9).

Public paths (documented): `/actuator/health*`, `/actuator/info`, `/v3/api-docs*`,
`/swagger-ui*`.

## Persistence

Flyway owns the schema (`ddl-auto=validate`): `V1__trading_schema.sql` (tables orders /
executions / execution_misc_fees, FIX-code enum columns with CHECK constraints,
`DECIMAL(19,4)` quantities / `DECIMAL(19,8)` prices, `DATETIME(6)` UTC, deliberate index
and constraint names) and `db/seed/V2__trading_dev_seed.sql` (deterministic demo data:
6 orders / 8 executions incl. a correction chain, a cancellation chain, and an allocated
fill; owners are the Keycloak dev usernames).

`spring.flyway.locations` defaults to `db/migration,db/seed` for the local/demo profile;
production-style deployments override it to `classpath:db/migration` only.

## Running locally

```powershell
./scripts/dev-up.ps1                                   # MySQL (host port 3307), Keycloak, Redis
./mvnw -pl services/trading-service spring-boot:run    # http://localhost:8081/swagger-ui.html
```

If the Keycloak volume predates the `trading-service-aud` realm scope, re-import the realm
once: `docker compose down keycloak -v` (wipes only Keycloak data) then `./scripts/dev-up.ps1`.
Obtain dev tokens from `http://localhost:8180/realms/tcs` (users `tcs-admin`, `investor1`,
`investor2`; machine client `allocation-engine`).

## Tests

```powershell
./mvnw -B -ntp -pl services/trading-service -am test                          # full suite
./mvnw -B -ntp -pl services/trading-service -am test "-DexcludedGroups=integration"  # fast loop, no Docker
```

Integration tests (tagged `integration`) use a singleton Testcontainers MySQL 8.4 and
RS256 test tokens validated by the production issuer+audience validator chain.

## Deferred integrations (documented boundaries, not gaps in invariants)

- `InstrumentReferencePort` / `AccountReferencePort` are implemented by local
  format-validation adapters; remote validation against enrichment-service (Phase 5) and
  account-service (Phase 4) replaces them behind the same ports.
- Order/execution lifecycle domain events are not currently published; Kafka event publication is
  owned by the allocation engine's transactional outbox.
- `/actuator/prometheus` exposes the registered domain counters (`tcs.trading.orders.*`,
  `tcs.trading.executions.recorded`, `tcs.trading.allocation_state.applied`), and the provisioned
  Grafana dashboards consume them.
