# Trading Capture System

A distributed trading capture platform: four Spring Boot services (trading, account, enrichment, allocation engine), a Next.js admin application, and an event-driven FIX allocation pipeline over Kafka — with MySQL, Redis, Keycloak (OAuth 2.0 / OIDC), Docker, Kubernetes, and Jenkins.

**Status: event contracts + allocation ingestion (Phases 6–7 of 14).** trading, account, and enrichment services are implemented (Phases 3–5); the Avro event contracts, the Kafka + Schema Registry local stack, and the allocation-engine FIX ingestion pipeline (consume → persist raw → parse → validate → outbox events, with retry/DLT) are in place. Allocation orchestration is Phase 8. `docs/status/current-status.md` is the authoritative record of what exists and what has actually been verified — nothing in this README claims more than that.

## Layout

| Path | Contents |
|---|---|
| `services/` | trading-service (8081), account-service (8082), enrichment-service (8083), allocation-engine (8084) — Spring Boot 3.5 / Java 21 |
| `apps/admin-web` | Next.js 14 admin frontend (migrated foundation; typed clients + Auth.js session rework arrive in Phase 10) |
| `libs/` | observability-support (correlation IDs + structured logging), security-support (shared resource-server pieces), event-contracts (Avro schemas + topic catalog), test-support (Testcontainers singletons + JWT fixtures) |
| `contracts/openapi` | API contracts per service |
| `docs/` | architecture, ADRs, event catalog, master plan, status, reference-investigation records |
| `infrastructure/`, `scripts/`, `docker-compose.yml` | local dev stack (MySQL + Redis + Keycloak + Kafka/Schema Registry; observability in Phase 11) |

## Quickstart (current phase)

Prereqs: JDK 21+ (machine JDK 24 works — build targets 21), Node 22+, Docker Desktop.

```powershell
# backend build + tests
./mvnw -B -ntp verify

# frontend
corepack pnpm install
corepack pnpm --filter admin-web lint ; corepack pnpm --filter admin-web typecheck ; corepack pnpm --filter admin-web build

# local infrastructure (MySQL :3307 by default — see .env.example, Redis :6379, Keycloak :8180 with
# realm `tcs`, Kafka :9092 + Schema Registry :8085 with topics registered by the one-shot kafka-init)
./scripts/dev-up.ps1     # docker compose up + health wait
./scripts/dev-down.ps1   # stop (add -Wipe to reset data)
```

Copy `.env.example` to `.env` for overrides; every committed credential is a documented development placeholder.

## Ground rules

- The sibling `../tcs-reference` directory holds historical course material: strictly read-only, never built, never committed here; selected logic was ported per `docs/reference/source-of-truth.md`.
- Architecture decisions are recorded in `docs/adr/`; the implementation must match them or supersede them explicitly.
- No fabricated metrics or claims: verification evidence lives in `docs/status/` and, at release, `docs/release/final-verification.md`.
