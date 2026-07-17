# allocation-engine

Kafka-driven ingestion of FIX 4.4 AllocationInstruction (35=J) messages (Phase 7 scope). Owns the `tcs_allocation` schema. Port 8084. **No business REST endpoints yet** — the service consumes, validates, persists, and publishes events; Phase 8 adds the allocation orchestration (M2M validation, smart selection, state transitions beyond RECEIVED/INVALID, recovery).

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

Outcome events (see `docs/contracts/event-catalog.md`): `AllocationReceived` (accepted), `AllocationRejected` + `ExceptionReported` (field-invalid), `ExceptionReported` (parse failures, retry exhaustion). Business duplicates emit nothing.

## Idempotency (ADR-0007)

- `uq_raw_messages_coordinates (topic, kafka_partition, kafka_offset)` absorbs broker redelivery; retry/DLT hops map back to the original coordinates via spring-kafka's `kafka_original-*` headers, so one inbound message is always one row with a `delivery_count` trail.
- `uq_allocations_business_key (alloc_id, alloc_trans_type)` absorbs upstream resends: recognized, marked `DUPLICATE`, acknowledged, no second event set.
- Offsets commit only after the durable work of an attempt completes (`ack-mode: record`, listener returns = attempt done). The Redis dedup pre-filter arrives with Phase 8.

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

`raw_messages` (verbatim payload, processing state, failure category/summary, correlation) · `allocations` (business key, ADR-0006 state column — full state list in the CHECK, Phase 7 writes RECEIVED/INVALID) · `allocation_orders` / `allocation_executions` / `allocation_accounts` (+ nested `allocation_misc_fees`) in wire order · `validation_errors` + `validation_error_fields` (collect-all output) · `outbox_event`. `BigDecimal` DECIMAL(19,4)/(19,8), UTC DATETIME(6), enums as explicit string codes; no seeds (data arrives via Kafka).

## Validation stages

1. Transport: `ErrorHandlingDeserializer` (undeserializable record → acked + logged + metric).
2. FIX syntax/structure: QuickFIX/J `fromString` with the FIX 4.4 dictionary.
3. Message type: only 35=J proceeds.
4. Field validation: dictionary-required presence, formats (decimal scale caps 4/8, dates, timestamps), value domains (AllocTransType 0/1/2, Side 1/2), group-required fields — **all errors collected** (ADR-0012).
5. Intra-message consistency: Replace/Cancel ⇒ RefAllocID; Σ AllocQty = Quantity.

Cross-service (logical) validation is Phase 8.

## Configuration

Env overrides (defaults in `application.yml`): `ALLOCATION_DB_HOST/PORT/NAME/USER/PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` (localhost:9092), `SCHEMA_REGISTRY_URL` (http://localhost:8085), `OIDC_ISSUER`. Knobs: `tcs.outbox.*` (poll interval 500 ms, batch 50, publish timeout, `enabled` for tests), `tcs.ingest.retry.*` (attempts/backoff), `tcs.ingest.inbound-topic`.

Security: OAuth2 resource server via `libs/security-support` (issuer + audience `allocation-engine`); only actuator health/info is public. Metrics: `tcs.allocation.ingest.*` (consumed, accepted, rejected, parse_failed, unsupported_type, duplicate_delivery, duplicate_business, retried_delivery, dead_lettered, outbox_published, outbox_publish_failed). Correlation: `x-correlation-id` Kafka header → MDC → outbox → event metadata + headers.

## Testing

```powershell
.\mvnw.cmd -B -ntp -pl services/allocation-engine -am test
```

Unit tests run without Docker; integration tests use singleton Testcontainers (MySQL 8.4 + cp-kafka 7.9.1, matching compose) with the in-memory `mock://` schema registry. The retry/DLT suite exercises real broker retry topology with fast deterministic backoff. Local end-to-end against compose: `./scripts/dev-up.ps1` (Kafka + Schema Registry + topics via `kafka-init`), then run the service and produce a FIX string onto `tcs.fix.allocation-instruction.v1`.
