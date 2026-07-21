# enrichment-service

Read-mostly reference data: instruments and alternate identifiers, exchanges, currencies,
settlement options, and fee rules (`tcs_enrichment` schema, port 8083), with Redis-backed
read-through caching. See `contracts/openapi/enrichment-service.openapi.yaml` for the API
contract of record; the read-mostly and Redis-fallback rules are summarized below.

## Domain rules

**Instruments & identifiers**
- An instrument references an exchange (MIC) and a currency; both must already exist (else 400
  `INVALID_REFERENCE`). Duplicate instrument IDs are 409. Instruments start `ACTIVE`; admins
  activate/deactivate.
- Alternate identifiers are normalized (trimmed, upper-cased). `CUSIP`/`ISIN`/`SEDOL` are
  **globally unique** (enforced in the service); an `EXCHANGE_SYMBOL` is unique **per exchange**
  (enforced by the DB unique constraint `(source, id_value, exchange_mic)`), so the same ticker
  can exist on multiple exchanges.
- Lookup by identifier: a global identifier resolves to at most one instrument (404 if none). An
  exchange symbol that matches more than one instrument is **ambiguous** → 409
  `AMBIGUOUS_IDENTIFIER`; pass `exchange` to disambiguate.

**Other reference data**
- Exchanges (MIC), currencies (with minor units), settlement options (with settlement-day
  counts), and fee rules — admin CRUD + read lookups. Duplicates are 409.
- Fee rules carry optional dimensions (`exchangeMic`, `instrumentType`, `currencyCode`); a null
  dimension is a wildcard. `GET /api/v1/fee-rules/applicable` returns the active rules matching a
  trade's dimensions. This service stores and serves fee rules — it does **not** compute fees.

## Redis caching (non-authoritative, ADR-0003 / ADR-0007)

- Read-through cache for instrument-by-id, instrument-by-identifier, exchange-by-MIC,
  currency-by-code, and the active fee-rule set; TTL from `tcs.cache.ttl` (default 15m).
- Administrative mutations **invalidate** the affected keys (fee rules use one active-set key, so
  a single eviction covers every dimension combination).
- Every cache operation **degrades gracefully**: a Redis outage or serialization error is logged
  and counted (`tcs.enrichment.cache.error`), then treated as a miss (reads) or no-op
  (writes/evictions). The database is always authoritative — correctness never depends on Redis
  (proven by `RedisFallbackIntegrationTest`). The actuator Redis health indicator is disabled so
  a Redis outage does not fail readiness.

## Authorization

Read endpoints require scope `enrichment:read`; administrative mutations require both scope
`enrichment:admin` and realm role `ADMIN`. The admin-web client can issue the scope to signed-in
users, but an `INVESTOR` token carrying that scope alone is still forbidden. The machine namespace
`/internal/v1/instruments/lookup` (batch resolution for allocation-engine / trading-service)
requires `enrichment:read` or `allocation:process`. Missing/invalid tokens are 401, missing
scope/role 403 — same RFC 7807 problem shape with `errorCode` and `correlationId`. Tokens must carry
issuer `…/realms/tcs` **and** audience `enrichment-service` (realm `enrichment-service-aud`
client scope). The enforced per-endpoint authorization matrix is in
`SECURITY.md`; `LiveKeycloakAuthorizationTest` proves it against a real
Keycloak container with real issued tokens (Phase 9).

Public paths: `/actuator/health*`, `/actuator/info`, `/v3/api-docs*`, `/swagger-ui*`.

## Persistence

Flyway owns the schema (`ddl-auto=validate`): `V1__enrichment_schema.sql` (exchanges,
currencies, settlement_options, instruments, instrument_alt_ids, fee_rules; enum string-code
CHECKs; per-exchange identifier uniqueness; FKs to exchanges/currencies) and
`db/seed/V2__enrichment_dev_seed.sql` (deterministic reference data: 3 exchanges, 4 currencies,
4 settlement options, 7 instruments — incl. an `INACTIVE` one and a deliberately-ambiguous `XYZ`
symbol on two exchanges — and 5 fee rules; AAPL/MSFT align with the account-service seed).

`spring.flyway.locations` defaults to `db/migration,db/seed`; production-style deployments
override it to `classpath:db/migration` only.

## Running locally

```powershell
./scripts/dev-up.ps1                                     # MySQL (3307), Redis (6379), Keycloak
./mvnw -pl services/enrichment-service spring-boot:run   # http://localhost:8083/swagger-ui.html
```

## Tests

```powershell
./mvnw -B -ntp -pl services/enrichment-service -am test                                 # full suite
./mvnw -B -ntp -pl services/enrichment-service -am test "-DexcludedGroups=integration"  # fast loop, no Docker
```

Integration tests (tagged `integration`) use singleton Testcontainers MySQL 8.4 and Redis 7.4
and RS256 test tokens validated by the production issuer+audience validator chain.

## Deferred integrations (documented boundaries)

- Enrichment domain events are not currently published; reference data is exposed through its REST
  contract and remains authoritative in its owned schema.
- `/actuator/prometheus` exposes the registered domain/cache counters
  (`tcs.enrichment.cache.*`, `tcs.enrichment.reference.mutations`) for Prometheus and the
  provisioned Grafana dashboards.
- The `enrichment-service-aud` realm client scope is import-validated only until the dev Keycloak
  volume is re-imported; wipe/recreate the local Keycloak volume after realm-definition changes.
