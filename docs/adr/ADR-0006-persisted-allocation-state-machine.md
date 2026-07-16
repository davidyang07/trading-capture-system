# ADR-0006: Allocation processing is a persisted, recoverable state machine

Status: Accepted (2026-07-14)

## Context
Allocation processing spans several remote calls (enrichment, trading, account) that can fail transiently mid-pipeline. With no distributed transaction, the engine must resume safely after crashes and duplicate deliveries.

## Decision
Each allocation row carries a state column: `RECEIVED → (INVALID | VALIDATED | REJECTED) → (EXECUTIONS_RELEASED →)? EXECUTIONS_ALLOCATED → COMPLETED`, with `PARSE_FAILED` (raw message only) and `FAILED` (retries exhausted) as additional terminals. Every step commits its state (and outbox rows) before proceeding; every step is idempotent (selection recomputed; trading-service updates keyed by allocationId; unique constraints absorb re-inserts). A recovery job resumes non-terminal allocations on startup/schedule. Transition table lives in `docs/architecture/event-flows.md` and is enforced in code; illegal transitions throw.

## Consequences
- Crash/redelivery at any point resumes from persisted state instead of repeating side effects — verified by a kill-and-resume integration test (Phase 8).
- Replace/Cancel release semantics get explicit intermediate state (`EXECUTIONS_RELEASED`) rather than implicit multi-step mutations.
- Rejected: Saga frameworks / Axon (heavy for one pipeline), in-memory orchestration (unrecoverable), Kafka Streams (wrong tool for REST-coordinating workflow).
