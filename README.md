# Trading Capture System

A distributed, event-driven **trade capture platform** built on **Spring Boot** and **Apache Kafka**.
It captures orders and executions, maintains immutable trade and ledger history, enriches reference
data, and processes FIX 4.4 allocation instructions through a resilient, crash-recoverable pipeline.

Four independently deployable services own their own MySQL schemas, communicate over authenticated
REST and versioned Avro events, and are secured end to end with OAuth 2.0 / OpenID Connect. A
Next.js operator console, shared platform libraries, Testcontainers-backed test suites, hardened
container images, Kubernetes manifests, and a Jenkins pipeline complete the system.

---

## Architecture

```
                       ┌───────────────────────────── OAuth2 / OIDC (Keycloak, realm "tcs") ─────────────────────────────┐
                       │                                                                                                  │
                       ▼                                                                                                  ▼
   ┌──────────────┐   REST    ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
   │  API client  │ ────────▶ │  trading-service │        │  account-service │        │ enrichment-service│
   │ (M2M / user) │           │   orders +       │        │  accounts +      │        │  instruments +    │
   └──────────────┘           │   executions     │        │  cash/security   │        │  reference data   │
                              │   (tcs_trading)  │        │  ledgers         │        │  (tcs_enrichment) │
                              └────────┬─────────┘        │  (tcs_account)   │        └─────────┬────────┘
                                       │                  └────────┬─────────┘                  │
                                       │  internal M2M APIs (resolve executions, validate accounts, resolve instruments)
                                       │                           │                            │
                                       └───────────────┬───────────┴────────────────────────────┘
                                                       ▼
   FIX 4.4 AllocationInstruction ──▶ Kafka ──▶ ┌──────────────────────┐ ──▶ transactional outbox ──▶ Kafka (Avro events)
                                                │   allocation-engine  │
                                                │  ingest · validate · │
                                                │  orchestrate         │
                                                │  (tcs_allocation)    │
                                                └──────────────────────┘
```

| Service | Port | Schema | Responsibility |
|---|---|---|---|
| **trading-service** | 8081 | `tcs_trading` | Orders and executions; append-only correction/cancellation chains; server-derived cum/leaves/avg quantities; execution allocation state |
| **account-service** | 8082 | `tcs_account` | Financial accounts with lifecycle; immutable cash and security ledgers; balance derivation; batch account validation |
| **enrichment-service** | 8083 | `tcs_enrichment` | Instruments, exchanges, currencies, settlement options, and fee rules; Redis read-through caching with graceful fallback |
| **allocation-engine** | 8084 | `tcs_allocation` | Kafka-driven FIX allocation ingestion, cross-service validation, and a persisted, recoverable allocation state machine with a transactional outbox |

Each service owns its schema exclusively — no service reads another service's tables. All
cross-service data flows through authenticated internal APIs or events.

**Event-driven pipeline.** Allocation instructions arrive as FIX 4.4 messages on Kafka. The
allocation-engine records every inbound message verbatim, parses and validates it, and drives a
persisted state machine. Domain events are published through a **transactional outbox** — written in
the same transaction as the state change, then relayed to Kafka — so state and events never diverge.
Events are **Avro**, registered in Confluent Schema Registry under a `BACKWARD` compatibility policy.

**Security.** Every service is an OAuth 2.0 resource server validating both issuer and audience
against Keycloak. Endpoints declare explicit scopes (`order:*`, `execution:*`, `account:*`,
`enrichment:*`, `allocation:process`) with role and ownership rules enforced server-side; investor
tokens see only their own resources, and unowned resources return `404` rather than revealing
existence. Machine-to-machine calls use the client-credentials grant, and user and machine
identities are kept strictly separate, closing privilege-escalation and confused-deputy paths. The
trust-boundary model and the enforced per-endpoint authorization matrix are documented in
[`SECURITY.md`](SECURITY.md), validated by a live-Keycloak suite that imports the real realm and
issues real tokens.

**Resilience.** Cross-service calls run through typed HTTP clients wrapped in Resilience4j retry and
circuit breakers; only transient transport failures are retried, never business rejections or
authorization failures. The allocation state machine is crash-safe — every step commits before
proceeding, and work resumes from persisted state after a restart. Correlation IDs propagate across
REST and Kafka, and Micrometer metrics feed Prometheus and Grafana dashboards.

**Correctness.** `BigDecimal` throughout for money, prices, rates, and quantities over
`DECIMAL(19,4)` / `DECIMAL(19,8)` columns, with explicit UTC timestamps. Executions and ledger
entries are append-only: corrections and cancellations are new linked rows, never in-place updates.
Database constraints are authoritative for de-duplication, so retries and redeliveries converge on a
single outcome.

---

## Technology stack

- **Language / framework:** Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA / Hibernate,
  Spring Security (OAuth2 resource server + client)
- **Messaging:** Apache Kafka, Confluent Schema Registry, Apache Avro (versioned event contracts)
- **Data:** MySQL 8.4 (schema-per-service, Flyway migrations), Redis (read-through cache)
- **Identity:** Keycloak 26 (OAuth 2.0 / OpenID Connect)
- **Resilience & observability:** Resilience4j, Micrometer, Prometheus, Grafana
- **Protocols:** QuickFIX/J (FIX 4.4 AllocationInstruction parsing)
- **Frontend:** Next.js 15 (App Router), TypeScript, Auth.js (Keycloak PKCE + BFF token relay),
  react-final-form, PrimeReact
- **Build & delivery:** Maven multi-module, pnpm workspace, Docker Compose, digest-pinned non-root
  images, Trivy, Kubernetes, Jenkins

**Verification.** A test pyramid runs per service: JUnit 5 + Mockito unit tests, `@WebMvcTest`
authorization slices, Testcontainers integration tests against real MySQL, Kafka, and Redis
(covering schema constraints, idempotency, concurrency, and crash-and-resume), Avro contract and
compatibility tests, WireMock HTTP-client tests, and 16 full-system scenarios driving the live stack
over REST, Kafka, and Playwright. A JaCoCo gate (80% line / 70% branch) is enforced on `verify`;
admin-web adds jest, Testing Library, jest-axe, and Playwright coverage.

---

## Repository layout

| Path | Contents |
|---|---|
| `services/` | The four Spring Boot services |
| `libs/observability-support` | Correlation-ID propagation and structured logging |
| `libs/security-support` | Shared OAuth2 resource-server building blocks (issuer + audience validation, RFC 7807 responses) |
| `libs/event-contracts` | Avro event schemas, topic/header catalog, and contract tests |
| `libs/test-support` | Singleton Testcontainers and JWT test fixtures |
| `apps/admin-web` | Next.js / TypeScript admin operator application |
| `tests/full-system-tests` | Cross-service scenarios against the live stack over REST + Kafka |
| `contracts/openapi/` | OpenAPI contract of record per service |
| `infrastructure/` | Local dependencies, observability, Kubernetes manifests, Jenkins pipeline, Trivy policy |
| `scripts/`, `docker-compose*.yml` | Local development and full-system orchestration |

---

## Running the system

**Prerequisites:** JDK 21+, Docker Desktop, Node 22+ with Corepack.

```powershell
./scripts/dev-up.ps1                  # MySQL, Redis, Keycloak, Kafka, Schema Registry
./mvnw -B -ntp verify                 # build + full test suite (Testcontainers; Docker required)
```

Infrastructure listens on MySQL `3307`, Redis `6379`, Keycloak `8180` (realm `tcs`), Kafka `9092`,
and Schema Registry `8085`. Copy `.env.example` to `.env` to override any default; every credential
in the repository is a labelled development placeholder.

Run a service, then browse its OpenAPI UI at `/swagger-ui.html` and health probe at
`/actuator/health`:

```powershell
./mvnw -B -ntp -pl services/trading-service spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

Run the operator console:

```powershell
corepack pnpm install
corepack pnpm --filter admin-web dev
```

Run the whole platform — infrastructure plus all four containerized services, seeded and
health-checked — and the 16 end-to-end scenarios:

```powershell
./scripts/full-stack-up.ps1 -Build    # infra + services, seeded, waits until healthy
./scripts/run-scenarios.ps1           # pipeline (REST + Kafka) + UI (Playwright) scenarios
./scripts/full-stack-down.ps1 -Wipe   # tear down; -Wipe also drops volumes
```

The stack is disposable: no state lives outside the named volumes, so a wipe-and-rebuild restores a
clean, seeded environment from the pinned images.

---

## Delivery

Each service and admin-web ships a multi-stage Dockerfile with digest-pinned base images, a non-root
numeric UID, container-aware JVM heap sizing, OCI labels, and a dependency-free `HEALTHCHECK`.
Images are scanned with Trivy under a fail-on-fixable-`HIGH`/`CRITICAL` policy
(`infrastructure/trivy/`).

`infrastructure/kubernetes/` deploys the application tier with default-deny NetworkPolicies,
startup/readiness/liveness probes, resource requests and limits, read-only root filesystems, dropped
capabilities, and `seccompProfile: RuntimeDefault`; an Ingress exposes only admin-web, and
allocation-engine is pinned to a single replica for its outbox poller.

`infrastructure/jenkins/Jenkinsfile` is a declarative pipeline: checkout → secret scan (gitleaks) →
lint and static analysis → backend tests + JaCoCo gate → frontend tests + build → package → build
images → Trivy scan → push → manual-gated deploy. Tests are never skipped, and credentials come from
the Jenkins credentials store.
