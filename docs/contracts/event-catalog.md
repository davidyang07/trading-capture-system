# Event Catalog

Design contract for Phases 6–8. Schemas will live exclusively in `libs/event-contracts/src/main/avro/`; this catalog and those `.avsc` files must stay synchronized (checked at phase gates). Nothing below exists on a broker yet.

## Conventions

- Topic naming: `tcs.<owning-domain>.<event>.v<major>`; retry/DLT suffixes `-retry-<n>` / `-dlt` (inbound processing only).
- Serialization: Avro via Confluent Schema Registry, **TopicNameStrategy** (one event type per topic), compatibility **BACKWARD** per subject.
- Partitions (local): 3. Key = aggregate ID (per-aggregate ordering).
- Envelope: every event embeds `EventMetadata { eventId: uuid-string, eventType: string, schemaVersion: int, occurredAt: timestamp-millis, correlationId: string, causationId: nullable string, aggregateId: string }`.
- Headers: `x-correlation-id`, `x-causation-id`, `x-event-id` mirror the metadata for header-only consumers.
- Producer settings: `acks=all`, idempotent producer, compression (carried from the reference `drill.kafka` producer profile).
- Delivery: at-least-once via transactional outbox (ADR-0005); consumers dedupe on `eventId`.

## Inbound

| Topic | Value | Key | Consumer (group) | Error path |
|---|---|---|---|---|
| `tcs.fix.allocation-instruction.v1` | raw FIX 4.4 AllocationInstruction string | AllocID when known | allocation-engine (`allocation-engine`) | transient → `-retry-0/1/2` (expo backoff 1s→16s, 4 attempts) → `-dlt`; malformed/invalid = permanent, acked + ExceptionReported (never retried) |

## Outbound domain events (producer: allocation-engine, via outbox)

| Event / topic | Emitted on state | Payload core (beyond EventMetadata) | Key |
|---|---|---|---|
| `AllocationReceived` — `tcs.allocation.allocation-received.v1` | allocation persisted RECEIVED | allocId, transType, side, qty, tradeDate, raw-message ref | allocId |
| `AllocationValidated` — `tcs.allocation.allocation-validated.v1` | VALIDATED | allocId, orders[], account count | allocId |
| `AllocationRejected` — `tcs.allocation.allocation-rejected.v1` | INVALID or REJECTED | allocId (nullable for parse-only cases), error list (typed code, field, message) | allocId / raw-message id |
| `ExecutionsAllocated` — `tcs.allocation.executions-allocated.v1` | EXECUTIONS_ALLOCATED | allocId, execution ids + quantities | allocId |
| `ExecutionsReleased` — `tcs.allocation.executions-released.v1` | EXECUTIONS_RELEASED (Replace/Cancel) | allocId, refAllocId, released execution ids | allocId |
| `ExceptionReported` — `tcs.allocation.exception-reported.v1` | PARSE_FAILED / INVALID / REJECTED / FAILED | source ref (raw-message id or allocId), summary, errors[] | source ref |
| `SettlementRequested` — `tcs.allocation.settlement-requested.v1` | COMPLETED (New/Replace only) | allocId, accounts + amounts, settlement date | allocId |

## Consumers of outbound events

Within this system the outbound topics are integration contracts: consumed by integration/contract tests and demo console consumers, and documented as feeds for the external back-office, settlement, and exception-monitoring systems that the reference architecture assumed (its config declared `backOffice` and `exMon` topic destinations). No internal service consumes them for state — allocation → trading state updates are synchronous REST by design (read-your-writes for smart selection; see event-flows.md).

## Retention & schema evolution assumptions

Local/demo retention: broker default (7 days) — no compaction. Evolution: additive optional fields with defaults only (BACKWARD); breaking change ⇒ new `v<major+1>` topic + parallel publication window. Generated classes are build artifacts — never hand-edited, never committed outside `target/`.
