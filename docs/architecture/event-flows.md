# Event Flows — Allocation Pipeline

Design target (Phases 7–8). Topics, envelope, and schema rules: `docs/contracts/event-catalog.md`.

## Happy path — New allocation

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka<br/>tcs.fix.allocation-instruction.v1
    participant AE as allocation-engine
    participant DB as tcs_allocation (MySQL)
    participant TS as trading-service
    participant AS as account-service
    participant ES as enrichment-service
    participant OB as outbox poller
    participant KO as Kafka (event topics)

    K->>AE: raw FIX 35=J (String)
    AE->>DB: insert raw_message (unique topic/partition/offset)
    AE->>AE: QuickFIX/J parse + collect-all field validation
    AE->>DB: insert allocation RECEIVED + outbox(AllocationReceived)
    AE->>ES: validate instrument / identifiers (M2M)
    AE->>TS: fetch referenced orders + executions (M2M)
    AE->>AS: batch-validate accounts, funds/securities, status (M2M)
    AE->>DB: state VALIDATED + outbox(AllocationValidated)
    AE->>AE: smart selection (explicit execs, else all eligible remaining per order)
    AE->>TS: mark selected executions allocated (idempotent, keyed by allocationId)
    AE->>DB: state EXECUTIONS_ALLOCATED + outbox(ExecutionsAllocated)
    AE->>DB: state COMPLETED + outbox(SettlementRequested)
    OB->>DB: poll unpublished outbox rows (ordered)
    OB->>KO: publish Avro events, mark published
```

## Field-invalid / malformed input (permanent failures — never retried)

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka (inbound)
    participant AE as allocation-engine
    participant DB as tcs_allocation
    participant KO as Kafka (event topics)

    alt Unparseable FIX
        K->>AE: malformed payload
        AE->>DB: raw_message status PARSE_FAILED
        AE->>DB: outbox(ExceptionReported)
        AE-->>K: acknowledge (no retry)
    else Parseable but invalid fields / failed logical validation
        K->>AE: FIX 35=J
        AE->>DB: raw_message + allocation INVALID or REJECTED + ALL validation errors
        AE->>DB: outbox(AllocationRejected) + outbox(ExceptionReported)
        AE-->>K: acknowledge (no retry)
    end
    Note over AE,KO: outbox poller publishes as usual
```

## Transient failure → retry topics → DLT

Downstream 5xx/timeout or DB unavailability throws; spring-kafka non-blocking retries redeliver via `…-retry-0/1/2` (exponential backoff 1s→16s, 4 attempts total), then `…-dlt`. Because every step commits state first and every step is idempotent (raw_message/allocation unique keys; trading updates keyed by allocationId), redelivery resumes from the persisted state instead of repeating side effects. Exhaustion → allocation state `FAILED` + `ExceptionReported`.

## Replace / Cancel

- **Replace:** validate as above → release executions of the referenced allocation in trading-service → `EXECUTIONS_RELEASED` + outbox(ExecutionsReleased) → select + allocate replacement set → `EXECUTIONS_ALLOCATED` + outbox(ExecutionsAllocated) → `COMPLETED` + outbox(SettlementRequested).
- **Cancel:** validate reference → release executions → `EXECUTIONS_RELEASED` + outbox(ExecutionsReleased) → `COMPLETED` (no settlement event).

## Allocation state machine (persisted on the allocation row)

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: parsed OK
    [*] --> PARSE_FAILED: unparseable (raw_message only)
    RECEIVED --> INVALID: field validation errors
    RECEIVED --> VALIDATED: logical validation OK
    RECEIVED --> REJECTED: logical validation failed
    VALIDATED --> EXECUTIONS_RELEASED: Replace/Cancel release
    VALIDATED --> EXECUTIONS_ALLOCATED: New allocation applied
    EXECUTIONS_RELEASED --> EXECUTIONS_ALLOCATED: Replace reallocation
    EXECUTIONS_RELEASED --> COMPLETED: Cancel done
    EXECUTIONS_ALLOCATED --> COMPLETED: settlement requested
    RECEIVED --> FAILED: retries exhausted
    VALIDATED --> FAILED: retries exhausted
    EXECUTIONS_RELEASED --> FAILED: retries exhausted
    EXECUTIONS_ALLOCATED --> FAILED: retries exhausted
```

Terminal: `PARSE_FAILED`, `INVALID`, `REJECTED`, `COMPLETED`, `FAILED`. A recovery job resumes non-terminal allocations after restart (crash-resume test required in Phase 8).

## Correlation & causation

`correlationId` flows inbound header → MDC → every log line → outbound REST header → outbox → event header + `EventMetadata.correlationId`. `causationId` = the raw message ID (first-level events) or the triggering event's `eventId`.
