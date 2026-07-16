# Trading Capture System

A distributed trading capture platform: four Spring Boot services (trading, account, enrichment, allocation engine), a Next.js admin application, and an event-driven FIX allocation pipeline over Kafka — with MySQL, Redis, Keycloak (OAuth 2.0 / OIDC), Docker, Kubernetes, and Jenkins.

**Status: repository foundation (Phase 2 of 14).** The monorepo, build toolchain, local infrastructure, and contracts are in place; business features land phase by phase. `docs/status/current-status.md` is the authoritative record of what exists and what has actually been verified — nothing in this README claims more than that.

## Layout

| Path | Contents |
|---|---|
| `services/` | trading-service (8081), account-service (8082), enrichment-service (8083), allocation-engine (8084) — Spring Boot 3.5 / Java 21 |
| `apps/admin-web` | Next.js 14 admin frontend (migrated foundation; typed clients + Auth.js session rework arrive in Phase 10) |
| `libs/` | observability-support (correlation IDs + structured logging), event-contracts (Avro, Phase 6), test-support (Phase 3) |
| `contracts/openapi` | API contract skeletons per service |
| `docs/` | architecture, ADRs, event catalog, master plan, status, reference-investigation records |
| `infrastructure/`, `scripts/`, `docker-compose.yml` | local dev stack (MySQL + Redis + Keycloak now; Kafka, observability later phases) |

## Quickstart (current phase)

Prereqs: JDK 21+ (machine JDK 24 works — build targets 21), Node 22+, Docker Desktop.

```powershell
# backend build + tests
./mvnw -B -ntp verify

# frontend
corepack pnpm install
corepack pnpm --filter admin-web lint ; corepack pnpm --filter admin-web typecheck ; corepack pnpm --filter admin-web build

# local infrastructure (MySQL :3307 by default — see .env.example, Redis :6379, Keycloak :8180 with realm `tcs`)
./scripts/dev-up.ps1     # docker compose up + health wait
./scripts/dev-down.ps1   # stop (add -Wipe to reset data)
```

Copy `.env.example` to `.env` for overrides; every committed credential is a documented development placeholder.

## Ground rules

- The sibling `../tcs-reference` directory holds historical course material: strictly read-only, never built, never committed here; selected logic was ported per `docs/reference/source-of-truth.md`.
- Architecture decisions are recorded in `docs/adr/`; the implementation must match them or supersede them explicitly.
- No fabricated metrics or claims: verification evidence lives in `docs/status/` and, at release, `docs/release/final-verification.md`.
