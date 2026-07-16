# ADR-0005: Transactional outbox with a polling publisher

Status: Accepted (2026-07-14)

## Context
Allocation state changes and their domain events must not diverge (no dual-write), and the brief bans distributed transactions. No reference implements any outbox.

## Decision
allocation-engine writes an `outbox_event` row (id, aggregate type/id, event type, topic, key, serialized payload, headers, created_at, published_at, attempts) **in the same MySQL transaction** as the state change. A `@Scheduled` poller (~500 ms, batched, ordered by id) publishes pending rows to Kafka (`acks=all`) and marks them published. Delivery is at-least-once; consumers deduplicate by `EventMetadata.eventId`.

## Consequences
- Events are exactly as durable as the state they describe; crash between commit and publish only delays publication.
- Polling adds ≤1 s latency and an `outbox_backlog` gauge — both acceptable and observable.
- Rejected: Debezium/CDC (an extra infrastructure system that isn't defensible at portfolio scale — documented as the production-scale alternative); publish-then-commit or commit-then-publish without outbox (dual-write loss/phantom events).
