# Event Catalog

Contract of record for Phases 6–8. Schemas live exclusively in `libs/event-contracts/src/main/avro/`; this catalog and those `.avsc` files must stay synchronized (mechanically checked by `TopicConventionsTest`; see `libs/event-contracts/README.md`). **Status:** schemas + topics + the Phase 7 ingestion producers exist, and the Phase 8 orchestration producers (`AllocationValidated`, `ExecutionsAllocated`, `ExecutionsReleased`, `SettlementRequested`, plus the REJECTED/orchestration-FAILED `AllocationRejected`/`ExceptionReported` paths) are now implemented in the allocation orchestrator and covered by integration tests.

## Conventions

- Topic naming: `tcs.<owning-domain>.<event>.v<major>`; retry/DLT suffixes `-retry-<n>` / `-dlt` (inbound processing only).
- Serialization: Avro via Confluent Schema Registry, **TopicNameStrategy** (one event type per topic), compatibility **BACKWARD** per subject.
- Partitions (local): 3. Key = aggregate ID (per-aggregate ordering).
- Envelope: every event embeds `EventMetadata { eventId: uuid-string, eventType: string, schemaVersion: int, occurredAt: timestamp-millis, correlationId: string, causationId: nullable string, aggregateId: string }`.
- Headers: `x-correlation-id`, `x-causation-id`, `x-event-id` mirror the metadata for header-only consumers.
- Producer settings: `acks=all`, idempotent producer, compression.
- Delivery: at-least-once via transactional outbox (ADR-0005); consumers dedupe on `eventId`.

## Inbound

| Topic | Value | Key | Consumer (group) | Error path |
|---|---|---|---|---|
| `tcs.fix.allocation-instruction.v1` | raw FIX 4.4 AllocationInstruction string | AllocID when known | allocation-engine (`allocation-engine`) | transient → `-retry-0/1/2` (expo backoff 1s→16s, 4 attempts) → `-dlt`; malformed/invalid = permanent, acked + ExceptionReported (never retried). Retry/DLT hops carry the original coordinates in `kafka_original-*` headers so every hop maps to one `raw_messages` row. |

## Outbound domain events (producer: allocation-engine, via outbox)

| Event / topic | Emitted on state | Payload core (beyond EventMetadata) | Key |
|---|---|---|---|
| `AllocationReceived` — `tcs.allocation.allocation-received.v1` | allocation persisted RECEIVED (Phase 7 ✅) | allocId, transType, side, qty (decimal 19,4), tradeDate (date), rawMessageId | allocId |
| `AllocationValidated` — `tcs.allocation.allocation-validated.v1` | VALIDATED (Phase 8 ✅) | allocId, orders[], account count | allocId |
| `AllocationRejected` — `tcs.allocation.allocation-rejected.v1` | INVALID (Phase 7 ✅) or REJECTED (Phase 8) | allocId (nullable when no business identity parsed), rawMessageId, complete typed error list (code, message, fields) | allocId / raw-message id |
| `ExecutionsAllocated` — `tcs.allocation.executions-allocated.v1` | EXECUTIONS_ALLOCATED (Phase 8 ✅) | allocId, execution ids + quantities | allocId |
| `ExecutionsReleased` — `tcs.allocation.executions-released.v1` | EXECUTIONS_RELEASED (Replace/Cancel, Phase 8 ✅) | allocId, refAllocId, released execution ids | allocId |
| `ExceptionReported` — `tcs.allocation.exception-reported.v1` | PARSE_FAILED / INVALID / retry-exhausted FAILED (Phase 7 ✅); REJECTED / orchestration FAILED (Phase 8) | sourceType (RAW_MESSAGE\|ALLOCATION), sourceRef, safe summary, errors[] | source ref |
| `SettlementRequested` — `tcs.allocation.settlement-requested.v1` | COMPLETED (New/Replace only, Phase 8 ✅) | allocId, accounts + amounts, settlement date | allocId |

## Consumers of outbound events

Within this system the outbound topics are integration contracts: consumed by integration/contract tests and demo console consumers, and documented as feeds for external back-office, settlement, and exception-monitoring systems. No internal service consumes them for state — allocation → trading state updates are synchronous REST by design (read-your-writes for smart selection; see event-flows.md).

## Retention & schema evolution assumptions

Local/demo retention: broker default (7 days) — no compaction. Evolution: additive optional fields with defaults only (BACKWARD); breaking change ⇒ new `v<major+1>` topic + parallel publication window. Generated classes are build artifacts — never hand-edited, never committed outside `target/`.

## Implementation notes (Phase 6–7)

- Constants: `com.tradingcapture.events.EventTopics` / `EventHeaders` are the in-code form of this catalog (sync-checked by test).
- Decimals are Avro `decimal` logical types (precision 19, scale 4 money/qty, 8 prices) → `BigDecimal`; producers must emit the exact scale (no silent rounding). Dates are `date`, instants `timestamp-millis`.
- Local infrastructure: cp-kafka 7.9.1 (KRaft) on :9092 + Schema Registry on :8085 (BACKWARD default) in docker-compose; `kafka-init` registers all 12 topics (3 partitions each) deterministically.
- Publication: allocation-engine transactional outbox → poller (500 ms, id-ordered, stop-on-failure to preserve per-aggregate order) → `acks=all` idempotent snappy producer. At-least-once; consumers dedupe on `EventMetadata.eventId`.
- Schema registration: the producer auto-registers subjects on first publication (`auto.register.schemas=true`) — the local/demo mechanism; the registry still enforces BACKWARD at registration. Production-style CI-driven registration (auto-register off) is a Phase 13 concern.
- Duplicate semantics: a resend reusing an existing `(AllocID, AllocTransType)` is absorbed as a business duplicate **regardless of the original outcome** — including a resend of a rejected (INVALID) instruction. Corrections must arrive as Replace (new AllocID + RefAllocID), which is standard FIX practice.
