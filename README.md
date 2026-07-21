# Trading Capture System

A distributed, event-driven **trading capture platform** built on Spring Boot and Kafka. It
captures orders and executions, maintains immutable trade and ledger history, enriches reference
data, and processes FIX allocation instructions through a resilient, recoverable pipeline — with
schema-per-service data ownership, versioned Avro event contracts, and OAuth 2.0 / OpenID Connect
security throughout.

The system is composed of four independently deployable Spring Boot services, a set of shared
libraries, a versioned event-contract module, and a local infrastructure stack (MySQL, Redis,
Keycloak, Kafka, and Schema Registry) wired together with Docker Compose.

---

## Current implementation status

The portfolio build is feature-complete across the application and delivery layers:

| Area | Current state |
|---|---|
| Services and data | Four Spring Boot services with schema-per-service ownership, Flyway migrations, immutable execution/ledger history, and UTC/`BigDecimal` contract discipline |
| Messaging | Kafka FIX ingestion, versioned Avro contracts, Schema Registry compatibility checks, durable idempotency, and transactional-outbox publication |
| Security | Keycloak OAuth 2.0/OIDC, issuer + audience validation, scopes/roles/ownership enforcement, Auth.js BFF sessions, and live-token authorization tests |
| Frontend | Typed Next.js workflows for orders, executions, accounts, and reference data with accessibility, unit tests, and deterministic Playwright coverage |
| Reliability | Persisted allocation orchestration, bounded retry/circuit breakers, crash recovery, correlation propagation, Prometheus metrics, and Grafana dashboards |
| Verification | Maven/JaCoCo quality gates, Testcontainers integration suites, 16 full-system scenarios, hardened multi-stage images, and Compose health orchestration |
| Delivery | Digest-pinned non-root images, Trivy policy, hardened Kubernetes app-tier manifests, and a declarative Jenkins build/scan/push/deploy pipeline |

Deployment activation still requires environment-owned infrastructure: real secrets, reachable
MySQL/Redis/Kafka/Schema Registry/Keycloak services, production DNS/TLS, a container registry, and
a Jenkins/Kubernetes environment. Those boundaries and recovery procedures are documented below.

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

**Service responsibilities and data ownership** (each service owns its own MySQL schema; no service
reads another's tables — all cross-service data flows through authenticated internal APIs or events):

| Service | Port | Owns | Responsibility |
|---|---|---|---|
| **trading-service** | 8081 | `tcs_trading` | Orders and executions; append-only correction/cancellation chains; server-derived cum/leaves/avg quantities; execution allocation state |
| **account-service** | 8082 | `tcs_account` | Financial accounts with lifecycle; immutable append-only cash and security ledgers; balance derivation; batch account validation |
| **enrichment-service** | 8083 | `tcs_enrichment` | Instruments, exchanges, currencies, settlement options, and fee rules; Redis read-through caching with graceful fallback |
| **allocation-engine** | 8084 | `tcs_allocation` | Kafka-driven FIX allocation ingestion, cross-service validation, and a persisted, recoverable allocation state machine with a transactional outbox |

**Event-driven pipeline.** Allocation instructions arrive as FIX 4.4 messages on Kafka. The
allocation-engine records every inbound message verbatim, parses and validates it, and drives a
persisted state machine. Domain events are published through a **transactional outbox** (written in
the same database transaction as the state change, then relayed to Kafka by a polling publisher) so
state and events never diverge. All events are **Avro** with schemas registered in Confluent Schema
Registry under a `BACKWARD` compatibility policy.

**Security.** Every service is an OAuth 2.0 resource server validating both issuer and audience
against the local Keycloak realm. Endpoints declare explicit scopes (`order:*`, `execution:*`,
`account:*`, `enrichment:*`, `allocation:process`) and role/ownership rules enforced server-side;
investor tokens can only see their own resources (unowned resources return `404`, never revealing
existence). Machine-to-machine calls use the OAuth 2.0 client-credentials grant. User and machine
identities are kept separate — a user token never carries a machine scope and a machine token never
carries a realm role — so privilege escalation and confused-deputy paths are closed. The full
trust-boundary model, roles-vs-scopes-vs-audiences design, and the **enforced per-endpoint
authorization matrix** are documented in [`SECURITY.md`](SECURITY.md);
a live-Keycloak test suite imports the real realm and validates the mappings with real issued tokens.

**Resilience.** Cross-service calls from the allocation-engine go through typed HTTP clients wrapped
in Resilience4j retry and circuit breakers. Only transient transport failures (timeouts, connection
loss, 5xx) are retried; business rejections and authorization failures are never retried. The
allocation state machine is crash-safe: every step commits its state before proceeding, and work
resumes from persisted state after a restart.

---

## Technology stack

- **Language / framework:** Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA / Hibernate,
  Spring Security (OAuth2 resource server + client)
- **Messaging:** Apache Kafka, Confluent Schema Registry, Apache Avro (versioned event contracts)
- **Data:** MySQL 8.4 (schema-per-service, Flyway migrations, `BigDecimal` money/quantities,
  UTC `DATETIME(6)`), Redis (read-through cache)
- **Identity:** Keycloak 26 (OAuth 2.0 / OpenID Connect)
- **Resilience:** Resilience4j (retry, circuit breaker), Micrometer metrics
- **Protocols:** QuickFIX/J (FIX 4.4 AllocationInstruction parsing)
- **Testing:** JUnit 5, Mockito, Spring MockMvc slices, Testcontainers (MySQL, Kafka, Redis),
  WireMock (HTTP client tests)
- **Frontend:** Next.js 15 (App Router), TypeScript, Auth.js (Keycloak PKCE + BFF token
  relay), react-final-form, PrimeReact; jest + Testing Library + jest-axe, Playwright
  (see [`apps/admin-web/README.md`](apps/admin-web/README.md))
- **Build & delivery:** Maven (multi-module aggregator), pnpm workspace, Docker Compose,
  digest-pinned Docker images, Trivy, Kubernetes, and Jenkins

---

## Repository layout

| Path | Contents |
|---|---|
| `services/` | The four Spring Boot services (trading, account, enrichment, allocation-engine) |
| `libs/observability-support` | Correlation-ID propagation and structured logging |
| `libs/security-support` | Shared OAuth2 resource-server building blocks (issuer + audience validation, RFC 7807 problem responses) |
| `libs/event-contracts` | Avro event schemas + topic/header catalog and contract tests |
| `libs/test-support` | Singleton Testcontainers (MySQL, Kafka, Redis) and JWT test fixtures |
| `apps/admin-web` | Next.js / TypeScript admin operator application |
| `tests/full-system-tests` | Opt-in cross-service scenarios against the live stack over REST + Kafka |
| `contracts/openapi/` | OpenAPI contract of record per service |
| `infrastructure/` | Local dependencies, observability, hardened Kubernetes manifests, Jenkins pipeline, and Trivy policy |
| `scripts/`, `docker-compose*.yml` | Local development and full-system orchestration |

---

## Getting started

### Prerequisites

- **JDK 21+** (the build targets Java 21; a newer JDK works — bytecode is pinned via `--release 21`)
- **Docker Desktop** (for the MySQL / Redis / Keycloak / Kafka / Schema Registry stack)
- **Node 22+** with Corepack (for the admin web application)

### 1. Start the infrastructure

```powershell
./scripts/dev-up.ps1        # docker compose up + wait for health
```

This brings up (host ports; override in `.env` — see `.env.example`):

| Component | Host port | Notes |
|---|---|---|
| MySQL 8.4 | `3307` | one schema per service, created on first start |
| Redis | `6379` | enrichment read-through cache |
| Keycloak 26 | `8180` | realm `tcs` imported on start |
| Kafka | `9092` | single-node KRaft |
| Schema Registry | `8085` | `BACKWARD` compatibility; topics registered by a one-shot `kafka-init` job |

```powershell
./scripts/dev-down.ps1      # stop the stack (add -Wipe to reset all data volumes)
```

Copy `.env.example` to `.env` to override any defaults. Every credential committed to this
repository is a clearly labelled development placeholder; real secrets come from the environment.

### 2. Build and test the backend

```powershell
./mvnw -B -ntp verify
```

This compiles all modules and runs the full test suite, including Testcontainers-based integration
tests (real MySQL, Kafka, and Redis) — Docker must be running.

### 3. Run a service

Each service is a standard Spring Boot application:

```powershell
./mvnw -B -ntp -pl services/trading-service spring-boot:run
# or, after `./mvnw -B -ntp package`:
java -jar services/trading-service/target/trading-service-0.1.0-SNAPSHOT.jar
```

Once running, each service serves:

- OpenAPI UI at `http://localhost:<port>/swagger-ui.html`
- Health probe at `http://localhost:<port>/actuator/health`

### 4. Run the admin web application

```powershell
corepack pnpm install
corepack pnpm --filter admin-web dev
```

### 5. Run the full stack and the 16 end-to-end scenarios

The full stack runs the four services containerized alongside the infrastructure, with the
deterministic seed data applied automatically and a health-wait, from one command:

```powershell
.\scripts\full-stack-up.ps1 -Build      # infra + 4 services, seeded, waits until healthy
.\scripts\run-scenarios.ps1             # 16 scenarios: pipeline (REST + Kafka) + UI (Playwright)
.\scripts\full-stack-down.ps1 -Wipe     # tear down (‑Wipe also drops volumes)
```

- **Pipeline scenarios** run via the `full-system-tests` module (`FULL_STACK=1`) driving the
  services over real HTTP + Kafka + Keycloak.
- **UI scenarios** run via the Playwright `*.full.spec.ts` specs (`E2E_FULL_STACK=1`) against a
  live admin-web + the containerized services.
- The executable scenario catalog lives in `tests/full-system-tests`; Playwright full-stack flows
  live in `apps/admin-web/e2e/*.full.spec.ts`.

Keycloak advertises a fixed issuer (`http://localhost:8180`) with dynamic backchannel so the
browser, the host test harness, and the containers agree on one token issuer; services fetch JWKS
from the in-network URL via `OIDC_JWK_SET_URI` (the frontchannel/backchannel issuer split).
admin-web runs on the host for the UI
E2E; an opt-in `--profile ui` starts a fully containerized admin-web for a compose-only demo.

**Rollback / recovery.** The stack is disposable: `full-stack-down.ps1 -Wipe` removes containers
and volumes; `full-stack-up.ps1 -Build` recreates everything from the pinned images + seeds. No
state lives outside the named volumes. Container-image hardening, Kubernetes manifests, and the
Jenkins delivery pipeline are described in **Delivery & deployment** below.

---

## Delivery & deployment

The delivery path turns the verified build into hardened, scannable images and a repeatable
deployment.

**Hardened images.** Each service and admin-web ships a multi-stage Dockerfile with
**digest-pinned** base images (supply-chain reproducibility), a **non-root numeric UID**
(`10001` for services, `1001` for admin-web — so Kubernetes `runAsNonRoot` is satisfiable),
container-aware JVM heap sizing, OCI image labels, and a dependency-free `HEALTHCHECK`. Build any
image from the repo root:

```powershell
docker build -f services/trading-service/Dockerfile -t tcs/trading-service:validation .
docker build -f apps/admin-web/Dockerfile           -t tcs/admin-web:validation .
```

**Vulnerability scanning.** `infrastructure/trivy/` holds the trivy policy (fail on fixable
`HIGH`/`CRITICAL`; `.trivyignore` for reviewed, time-boxed exceptions — empty by policy):

```powershell
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock `
  -v "${PWD}/infrastructure/trivy:/cfg:ro" `
  aquasec/trivy:0.72.0 image --config /cfg/trivy.yaml tcs/trading-service:validation
```

**Kubernetes.** `infrastructure/kubernetes/` contains plain manifests — namespace, a
token-less ServiceAccount, default-deny + scoped NetworkPolicies, per-service
ConfigMap/Deployment/Service, an example (placeholders-only) Secret, and an Ingress exposing only
admin-web. Every workload has startup/readiness/liveness probes, resource requests+limits, a
read-only root filesystem, dropped capabilities, and `seccompProfile: RuntimeDefault`;
allocation-engine is pinned to a single replica (`Recreate`) for its outbox poller. Schema-validate
with kubeconform (no cluster needed):

```powershell
docker run --rm -v "${PWD}/infrastructure/kubernetes:/manifests:ro" `
  ghcr.io/yannh/kubeconform:v0.8.0-alpine -strict -summary /manifests
```

See [`infrastructure/kubernetes/README.md`](infrastructure/kubernetes/README.md) for deploy,
rollback (`kubectl rollout undo`), and recovery procedures.

**CI pipeline.** `infrastructure/jenkins/Jenkinsfile` is a declarative pipeline: checkout → secret
scan (gitleaks) → lint/static → backend tests + JaCoCo gate → frontend tests + build → package →
build images → trivy scan → opt-in push → manual-gated deploy. **Tests are never skipped**, and no
credentials are embedded (they come from the Jenkins credentials store). Validate it with the
Jenkins declarative linter — see [`infrastructure/jenkins/README.md`](infrastructure/jenkins/README.md).

**Validated vs. executed (2026-07-21).** In this environment: Maven `clean verify` (605 tests
discovered: 595 passed and 10 opt-in full-system tests skipped, all coverage gates met) ✅; admin-web lint/type-check/build and
76 unit tests ✅; `docker build` for all five images ✅; Trivy vulnerability + secret scans for all
five images (0 fixable HIGH/CRITICAL findings) ✅; kubeconform (23/23 valid, strict) ✅; and both
Compose configurations ✅. The **Jenkins declarative linter**, live **kind** rollout, and opt-in
full-system/Playwright suites require external activation and are not claimed as executed here.

---

## API contracts & documentation

- **OpenAPI** contracts of record live in `contracts/openapi/` and are served live by each running
  service via springdoc.
- **Event contracts** (Avro schemas, topic/header conventions, and compatibility rules) live in
  [`libs/event-contracts`](libs/event-contracts/README.md).
- **Architecture and operational rationale** is kept with the behavior it governs: this README,
  [`SECURITY.md`](SECURITY.md), service READMEs, and the infrastructure READMEs.

---

## Testing strategy

A test pyramid is applied per service:

- **Unit** — domain rules and services (JUnit 5 + Mockito).
- **Web slices** — controller authorization and serialization (`@WebMvcTest` + Spring Security test).
- **Integration** — full flows against real infrastructure via singleton Testcontainers (MySQL,
  Kafka, Redis), including schema/constraint verification, idempotency, concurrency, and
  crash-and-resume behavior.
- **Contract** — Avro serialization round-trips and schema backward-compatibility.
- **HTTP clients** — WireMock stubs covering success, timeouts, retryable vs. non-retryable
  failures, and circuit-breaker behavior.
- **Full-system** — the 16 cross-service scenarios against the live stack (`full-system-tests`
  JUnit module over REST + Kafka, and Playwright UI specs); see step 5 above.

Run everything with `./mvnw -B -ntp verify`, or a single module with
`./mvnw -B -ntp -pl services/<name> -am test`. A **JaCoCo coverage gate** (80% line / 70% branch,
generated code excluded) is enforced on `verify`; the `full-system-tests` module and
the Playwright full specs self-skip unless the live stack is up (they are never disabled to hide a
defect).

---

## Engineering principles

- **Financial correctness first.** `BigDecimal` for all money, prices, rates, and quantities;
  `DECIMAL(19,4)` / `DECIMAL(19,8)` columns; explicit UTC timestamps everywhere.
- **Immutable audit history.** Executions and ledger entries are append-only; corrections and
  cancellations are new linked rows, never in-place updates.
- **Clear ownership boundaries.** Each service owns its schema and exposes a contract; no
  cross-schema access.
- **Idempotency by design.** Database constraints are authoritative for de-duplication; retries and
  redeliveries converge on a single outcome.
- **Server-enforced authorization.** UI checks are UX only; scopes, roles, and ownership are enforced
  in each service.

---

## Current deployment boundaries

- Kubernetes deploys the application tier only; stateful dependencies are supplied by the target
  environment and referenced by Service DNS.
- The committed Secret is an example containing development placeholders. Real values must come
  from Jenkins credentials and an external secret-management path.
- Production browser authentication requires externally reachable Keycloak and HTTPS ingress
  hostnames; the committed `tcs.local` values are local-demo defaults.
- Jenkins syntax validation and a live Kubernetes rollout require external controllers. The
  repository documents these activation checks without claiming they ran locally.

---

## Repository conventions

- Material architecture decisions are reflected in the public architecture, security, service,
  and infrastructure documentation and must be honored or explicitly superseded.
- Documentation is kept synchronized with actual behavior, contracts, and executed verification.
