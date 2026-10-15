# allocation-engine

Kafka-driven ingestion and orchestration of FIX 4.4 AllocationInstruction (35=J) messages. Owns
the `tcs_allocation` schema and serves actuator endpoints on port 8084. It intentionally exposes no
inbound business REST API: the service consumes, validates, persists, selects executions through
authenticated M2M dependencies, drives a recoverable state machine, and publishes Avro events from
a transactional outbox.

## Pipeline

```text
tcs.fix.allocation-instruction.v1 (raw FIX string)
  → raw_messages insert, verbatim, own transaction        (survives any later crash)
  → QuickFIX/J parse (dictionary groups, validation off)  → PARSE_FAILED on malformed/unsupported
  → collect-all field validation (ADR-0012)               → INVALID with ALL errors stored
  → allocation RECEIVED + repeating groups persisted
  → outbox rows in the same transaction (ADR-0005)
  → poller publishes Avro events (acks=all, idempotent producer)
```

Outcome events (schemas in `libs/event-contracts`): `AllocationReceived` (accepted),
`AllocationRejected` + `ExceptionReported` (field-invalid), `ExceptionReported` (parse failures,
retry exhaustion). Business duplicates emit nothing.

## Idempotency (ADR-0007)

- `uq_raw_messages_coordinates (topic, kafka_partition, kafka_offset)` absorbs broker redelivery; retry/DLT hops map back to the original coordinates via spring-kafka's `kafka_original-*` headers, so one inbound message is always one row with a `delivery_count` trail.
- `uq_allocations_business_key (alloc_id, alloc_trans_type)` absorbs upstream resends: recognized, marked `DUPLICATE`, acknowledged, no second event set.
- Offsets commit only after the durable work of an attempt completes (`ack-mode: record`, listener
  returns = attempt done). A fail-open Redis pre-filter accelerates duplicate detection while
  database constraints remain authoritative.

## Failure classification

| Failure | Classification | Terminal state |
|---|---|---|
| Unparseable FIX / bad field structure | permanent, acked | raw `PARSE_FAILED` / `MALFORMED_MESSAGE` |
| MsgType ≠ J | permanent, acked | raw `PARSE_FAILED` / `UNSUPPORTED_MESSAGE_TYPE` |
| Field validation errors | permanent, acked | raw `INVALID` (+ allocation `INVALID` when the identity parsed) |
| Business duplicate | permanent, acked | raw `DUPLICATE` |
| DB/infrastructure errors, unexpected exceptions | `TransientIngestionException` → retried | raw `FAILED` / `RETRY_EXHAUSTED` after exhaustion |

Retries are non-blocking `@RetryableTopic`: 4 attempts, exponential 1 s → 4 s → 16 s over `…-retry-0/1/2`, then `…-dlt`. The DLT handler stamps `FAILED` + records `ExceptionReported`; the DLT record keeps the payload and original coordinates for manual replay. Non-blocking retry relaxes strict per-key ordering while a record waits on a retry topic — accepted: processing is idempotent and Phase 8 validates references logically.

## Persistence (`V1__allocation_schema.sql`)

`raw_messages` (verbatim payload, processing state, failure category/summary, correlation) ·
`allocations` (business key and complete persisted state machine) · `allocation_orders` /
`allocation_executions` / `allocation_accounts` (+ nested `allocation_misc_fees`) in wire order ·
`validation_errors` + `validation_error_fields` (collect-all output) · `outbox_event`. Ingestion
writes `RECEIVED`/`INVALID`; orchestration drives the remaining legal states. `BigDecimal`
DECIMAL(19,4)/(19,8), UTC DATETIME(6), enums as explicit string codes; no seeds (data arrives via
Kafka).

## Validation stages

1. Transport: `ErrorHandlingDeserializer` (undeserializable record → acked + logged + metric).
2. FIX syntax/structure: QuickFIX/J `fromString` with the FIX 4.4 dictionary.
3. Message type: only 35=J proceeds.
4. Field validation: dictionary-required presence (plus SettlDate (64) on New/Replace — they complete by requesting settlement, so a missing date is rejected here rather than after executions are bound), formats (decimal scale caps 4/8, dates, timestamps), value domains (AllocTransType 0/1/2, Side 1/2), group-required fields — **all errors collected** (ADR-0012).
5. Intra-message consistency: Replace/Cancel ⇒ RefAllocID; Σ AllocQty = Quantity.

Cross-service logical validation follows field validation during orchestration.

## Allocation orchestration

Ingestion leaves accepted instructions at `RECEIVED` (or rejects them as `INVALID`). Orchestration
takes over from `RECEIVED`: it validates the instruction against the other services, selects backing executions,
binds/releases them in trading-service, and drives the allocation through the ADR-0006 state machine
to a terminal state, emitting domain events through the transactional outbox. A single scheduled
driver does this for both new work and crash recovery. Live Keycloak M2M token acquisition (the
`allocation-engine` client-credentials client + service-account scopes) is verified by
`SECURITY.md` and the live-Keycloak suites. Redis is an optimization only — the database is
authoritative. The admin UI has no allocation query view because this service intentionally
exposes no public query API.

### Persisted state machine

Ten persisted states: `RECEIVED INVALID VALIDATED REJECTED EXECUTIONS_ALLOCATED
EXECUTIONS_RELEASED COMPLETED FAILED REPLACED CANCELLED` (terminals: `INVALID REJECTED COMPLETED FAILED
REPLACED CANCELLED`).
`AllocationStateMachine` holds the legal-transition table; `Allocation.transitionTo` rejects any
illegal move (including out of a terminal) with `IllegalAllocationTransitionException` before mutating
state, and appends an `allocation_state_transitions` history row. Lifecycles:

- **New:** `RECEIVED → VALIDATED → EXECUTIONS_ALLOCATED → COMPLETED`.
- **Replace:** `RECEIVED → VALIDATED → EXECUTIONS_RELEASED → EXECUTIONS_ALLOCATED → COMPLETED` (release
  the original's executions, then allocate the replacement set).
- **Cancel:** `RECEIVED → VALIDATED → EXECUTIONS_RELEASED → COMPLETED` (no settlement event).
- **FAILED** is reachable from any working state when retries are exhausted.
- **Supersession:** the original a Replace/Cancel references moves `EXECUTIONS_ALLOCATED | COMPLETED →
  REPLACED | CANCELLED` in the same transaction as that successor's release. Only these two *live*
  states can be replaced or cancelled, so a superseded allocation is never replaced/cancelled twice and
  a Replace after a Cancel cannot resurrect it (`V3__allocation_supersession.sql` widens the status CHECKs).

Retryable-vs-terminal is **bookkeeping**, never a new state: `attempt_count`, `max_attempts`,
`last_attempt_at`, `next_attempt_at`, `failure_category` (`OrchestrationFailureCategory`), and a safe
`failure_summary`.

### Persistence (`V2__allocation_orchestration.sql`, `ddl-auto=validate`)

- `allocations` gains the bookkeeping columns above plus `claimed_by`/`claimed_at` (driver lease),
  `completed_at`, and `idx_allocations_next_attempt (status, next_attempt_at)` for recovery scans.
- `allocation_state_transitions` — append-only history (`uq_alloc_transitions_seq`), never deleted.
- `allocation_assignments` — one row per (allocation, execution) internal id
  (`uq_alloc_assignments_execution`), `allocated_qty > 0`, `released_qty ∈ [0, allocated_qty]`, status
  `ASSIGNED`/`RELEASED`. Release is exactly once (a second release throws; DB CHECK backs it).
- Optimistic `@Version` on the allocation aggregate. No cross-service foreign keys.

### Trading execution-resolution contract

`POST /internal/v1/executions/resolve` (trading-service, scope `allocation:process`, machine-only)
resolves the FIX `NoExecs` external ExecIDs (tag 17) to internal executions plus an eligibility
verdict (`ELIGIBLE`/`NOT_FOUND`/`CANCELLED`/`SUPERSEDED`/`ALREADY_ALLOCATED`) and the fields selection
needs. Read-only, per-item (an unknown id is `NOT_FOUND`, never a 4xx); investor/user tokens get 403.
Contract of record: `contracts/openapi/trading-service.openapi.yaml`.

### Dependency clients + resilience (`client/`)

Typed `TradingExecutionClient` (resolve + idempotent allocate/release), `AccountValidationClient`
(`/internal/v1/accounts/batch`), `InstrumentResolutionClient` (`/internal/v1/instruments/lookup`),
each on a Spring `RestClient` with per-service base URL + connect/read timeouts, a machine
**OAuth2 client-credentials** bearer (Spring Security `OAuth2AuthorizedClientManager` — token never
logged), and `X-Correlation-ID` propagated from the MDC. Business verdicts (inactive account,
unresolved execution) are ordinary 200 results — never exceptions. `RemoteCallExecutor` wraps each call
in the dependency's Resilience4j retry + circuit breaker: only transient transport failures
(timeout / connection loss / 5xx / 503) are retried and counted toward the breaker
(`RetryableDependencyPredicate`); 4xx business/authorization results are neither retried nor trip it; a
circuit-open surfaces as a retryable `UNAVAILABLE`. A failure to obtain the machine token is classified
the same way (`TokenAcquisitionException`): Keycloak unreachable, timing out, answering 5xx/429 or
`server_error`/`temporarily_unavailable` is a retryable `UNAVAILABLE`; a rejected client is `UNAUTHORIZED`. `DependencyFailureType → OrchestrationFailureCategory`
so a failure schedules a retry or escalates to terminal. Metrics:
`tcs.allocation.dep.calls{dependency,operation,outcome}` + resilience4j-micrometer.

### Logical validation (`validation/logical/`)

Runs after resolution, over a framework-free `LogicalValidationContext` (no HTTP/JPA types). Stages,
in deterministic order: **account** (exists/active/owner, plus cash for a buy and the position for a
sell — `AccountRequirements`: a buy needs the line's NetMoney, else its quantity at AllocAvgPx/AllocPrice
or the instruction's AvgPx, rounded up; a sell needs the allocated quantity of the instrument) → **instrument**
(found/ambiguous/inactive/exchange/currency/settlement) → **execution** (not-found/cancelled/superseded/
already-allocated/instrument+side match/allocatable qty/duplicate ref) → **quantity** (positive/
precision/account-sum/aggregate-exec-qty, which must equal the quantity exactly) → **transaction-type**
(New/Replace/Cancel prerequisites). A Cancel allocates nothing, so it runs the transaction-type stage only
(its original must exist and be live). A Replace may re-list executions still bound to the allocation it
replaces; they count at their full executed quantity because the release step frees them first. All
business errors are collected, de-duplicated, and ordered by stage → code → identifier.
`LogicalValidationResult` separates `Valid`, `BusinessRejected`, `DependencyUnavailable` (retryable),
`DependencyTerminal`, and `Invariant` — dependency failures are never mixed into the business-error
list, and there is no catch-all "approve on error" fallback.

### Deterministic execution selection (ADR-0017)

`ExecutionSelector` selects every eligible execution the instruction lists, each consumed **whole**,
and requires them to sum exactly to the requested quantity. Executions are never split: trading-service
binds a whole execution to one allocation, so taking part of one would bind (and strand) the rest. Less
eligible quantity is a typed `Insufficient`, more is a typed `ExcessQuantity` — never a partial success.
Sequencing uses a total order independent of input order: execution `transactTime` → `execId` →
`internalId`.
`BigDecimal` scale 4, `RoundingMode.UNNECESSARY`, no floating point; scale > 4 is rejected. Selection is
a pure snapshot calculation and does **not** reserve quantity — the mutation transaction re-reads
authoritative state under optimistic locking and the idempotent trading allocate call is the final
conflict boundary, so a stale selection fails safely and retries cannot over-allocate.

### Orchestration flows (`orchestration/`)

`AllocationOrchestrator` advances one allocation as state-guarded steps. Each step loads an immutable
snapshot (short read tx), performs remote calls **outside** any mutating transaction, then commits an
atomic mutation + same-transaction outbox row via `OrchestrationTx`:

- **New:** validate → select → idempotent trading ALLOCATE → persist assignments + `EXECUTIONS_ALLOCATED`
  + `ExecutionsAllocated` → `COMPLETED` + `SettlementRequested`. Rollback leaves no partial assignment,
  transition, or event; a re-run is a no-op (status guards); executions already allocated to this allocId
  are treated as ours, so a crash between the remote ALLOCATE and the local commit resumes cleanly.
- **Replace:** validate against the referenced original → release the original's executions (idempotent
  trading RELEASE, mark the original's assignments released, original → `REPLACED`) +
  `EXECUTIONS_RELEASED` + `ExecutionsReleased` → select + allocate the replacement set →
  `EXECUTIONS_ALLOCATED` → `COMPLETED` + `SettlementRequested`. A failure before the release commits
  leaves the original untouched.
- **Cancel:** validate against the original → release its executions (original → `CANCELLED`) +
  `EXECUTIONS_RELEASED` + `ExecutionsReleased` → `COMPLETED` (no settlement). Released quantity becomes
  available exactly once; a repeated Cancel is rejected (the original is no longer live) and emits no
  second release; history is preserved, never deleted.
- **The referenced original** (RefAllocID) is the New **or Replace** instruction with that AllocID, so a
  replacement can itself be replaced or cancelled later. If a concurrent Replace/Cancel retires the
  original first, the loser is rejected at its release step.

### Redis deduplication pre-filter (ADR-0007)

`AllocationDedupFilter` — key namespace `tcs:alloc:dedup:<allocId>:<transType>`, TTL `tcs.orchestration.dedup-ttl`
(30m). A hit is a hint to confirm against the database, never a blind discard, so a false positive cannot
drop valid work; any Redis failure fails open (miss / best-effort write) and the DB unique constraints +
per-allocation state remain authoritative. Metrics `tcs.allocation.dedup{result=hit|miss|error}`; the
actuator Redis health contributor is disabled so an outage never fails readiness.

### Orchestration + recovery driver (`OrchestrationDriver`, `OrchestrationClaimService`)

One `@Scheduled` driver serves both normal orchestration and recovery — resuming from persisted state
*is* the normal path. Each tick claims a bounded, id-ordered batch in a short transaction
(`SELECT … FOR UPDATE SKIP LOCKED` so concurrent workers claim disjoint rows), stamps a worker lease
(`claimed_by`/`claimed_at`) and increments the attempt counter, then commits before any remote call.
Eligible = non-terminal, `attempt_count < max_attempts`, due (`next_attempt_at` null or past), and not
actively leased (`claimed_at` null or older than the lease). It skips terminal/exhausted/not-yet-due/
leased rows; an expired lease is reclaimed. Retryable failures schedule an exponential backoff; exhausted
attempts escalate to `FAILED`. A row whose **last** attempt ended without recording any outcome (crash,
concurrency conflict, unexpected error) would never be claimed again, so once its lease expires the claim
transaction fails it terminally (`TERMINAL_ORCHESTRATION`, metric `driver{event=exhausted_failed}`).

**Releasing executions of unsuccessful allocations.** An allocation that ends `REJECTED` or `FAILED`
from `VALIDATED`, `EXECUTIONS_RELEASED` or `EXECUTIONS_ALLOCATED` may have executions bound to its
allocId in trading-service (an ALLOCATE whose response was lost, then retries ran out). It is flagged
`executions_release_pending` (V4), and `ExecutionReleaseDriver`, run on the same scheduler tick,
asks trading-service to RELEASE everything bound to that allocId until it succeeds. Releasing is
idempotent and releasing nothing is a no-op, and retries use the orchestration backoff with no attempt
cap. Metric: `tcs.allocation.orchestration.execution_release{outcome}`.

Metrics `tcs.allocation.orchestration.{steps,driver}` and
`tcs.allocation.dedup` are all low-cardinality (no allocation/account/execution/correlation ids as tags).

### Orchestration outbox events (ADR-0005)

`AllocationValidated`, `ExecutionsAllocated`, `ExecutionsReleased`, `SettlementRequested`, and for
unsuccessful outcomes `AllocationRejected` (every reason, as typed error codes) + `ExceptionReported` on
`REJECTED`, and `ExceptionReported` on `FAILED` (including rows failed as stranded) — recorded in
the same transaction as the state change (hard-guarded), Kafka key = business `allocId` (deterministic
per-allocation ordering), full metadata with correlation + causation (the inbound raw message), decimal
fidelity at scale 4. Execution identifiers in events are the internal execution ids stored on the
assignments (the ExecID→internal mapping is resolved once at orchestration entry), so they are stable and
identical across a New's `ExecutionsAllocated` and a later Replace/Cancel's `ExecutionsReleased`. Duplicate
events are impossible on retry/replay because the emitting transition is state-guarded and the outbox row
carries a unique event id. When the release driver frees executions that an `ExecutionsAllocated`
announced, it publishes `ExecutionsReleased` with `refAllocId = allocId` (the allocation released its own).

The outbox poller retries a failed publish in order and stops the batch, so per-aggregate ordering
holds. A row that can never be published is the exception: an undecodable payload, a record over the
broker's size limit, or a schema the registry refuses (409/422). Such a row is parked
(`outbox_event.parked_at`, V4) so it stops blocking every later event. It is logged at ERROR and
counted by the `tcs.allocation.outbox.parked` gauge (and `tcs.allocation.ingest.outbox_parked`), and it
needs an operator.

## Configuration

Env overrides (defaults in `application.yml`): `ALLOCATION_DB_HOST/PORT/NAME/USER/PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` (localhost:9092), `SCHEMA_REGISTRY_URL` (http://localhost:8085), `OIDC_ISSUER`. Knobs: `tcs.outbox.*` (poll interval 500 ms, batch 50, publish timeout, `enabled` for tests), `tcs.ingest.retry.*` (attempts/backoff), `tcs.ingest.inbound-topic`.

Security: OAuth2 resource server via `libs/security-support` (issuer + audience `allocation-engine`); only actuator health/info is public. Metrics: `tcs.allocation.ingest.*` (consumed, accepted, rejected, parse_failed, unsupported_type, duplicate_delivery, duplicate_business, retried_delivery, dead_lettered, outbox_published, outbox_publish_failed). Correlation: `x-correlation-id` Kafka header → MDC → outbox → event metadata + headers.

M2M clients: `tcs.clients.{account,enrichment,trading}.{base-url,connect-timeout,read-timeout}` (env `ACCOUNT_SERVICE_URL`/`ENRICHMENT_SERVICE_URL`/`TRADING_SERVICE_URL`), `resilience4j.retry|circuitbreaker.instances.{account,enrichment,trading}`, and a machine OAuth2 client registration `allocation-engine` (client-credentials; scopes `allocation:process,account:validate,enrichment:read`; secret from env — `ALLOCATION_CLIENT_SECRET`, dev placeholder only; live token flow is covered by the Keycloak integration suites). Dependency metrics: `tcs.allocation.dep.calls{dependency,operation,outcome}`. Access tokens / Authorization headers are never logged.

Orchestration/recovery driver: `tcs.orchestration.*` — `enabled` (default true; env `ORCHESTRATION_ENABLED`; tests disable it and drive `OrchestrationDriver.runOnce()` directly), `poll-interval` (1s), `batch-size` (20), `claim-lease` (2m), `retry-backoff-base`/`-multiplier`/`-max` (5s / 2.0 / 10m), `max-steps-per-run` (6), `dedup-ttl` (30m). Redis: `spring.data.redis.host`/`port` (env `REDIS_HOST`/`REDIS_PORT`); `management.health.redis.enabled=false` so a Redis outage never fails readiness. Orchestration metrics: `tcs.allocation.orchestration.steps{step,outcome}`, `tcs.allocation.orchestration.driver{event}`, `tcs.allocation.dedup{result}` — all low-cardinality.

## Testing

```powershell
.\mvnw.cmd -B -ntp -pl services/allocation-engine -am test                                  # full suite
.\mvnw.cmd -B -ntp -pl services/allocation-engine -am test "-DexcludedGroups=integration"   # fast loop, no Docker
```

Unit tests run without Docker; integration tests (tagged `integration`) use singleton Testcontainers (MySQL 8.4 + cp-kafka 7.9.1, matching compose) with the in-memory `mock://` schema registry. The retry/DLT suite exercises real broker retry topology with fast deterministic backoff. Local end-to-end against compose: `./scripts/dev-up.ps1` (Kafka + Schema Registry + topics via `kafka-init`), then run the service and produce a FIX string onto `tcs.fix.allocation-instruction.v1`.
