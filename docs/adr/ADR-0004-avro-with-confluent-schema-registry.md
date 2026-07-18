# ADR-0004: Avro + Confluent Schema Registry for domain events

Status: Accepted (2026-07-14)

## Context
Versioned event schemas are required, with Avro preferred when justified. The Confluent serializer stack is a natural fit, but the system needs a shared event envelope and locally-owned domain schemas.

## Decision
All seven domain events are Avro `SpecificRecord`s generated from `.avsc` files in `libs/event-contracts` (avro-maven-plugin; generated code never hand-edited). Confluent Schema Registry enforces **BACKWARD** compatibility with **TopicNameStrategy** (one event type per topic). Every event embeds a shared `EventMetadata` record: `eventId, eventType, schemaVersion, occurredAt, correlationId, causationId, aggregateId`. The inbound allocation topic stays a raw FIX string (faithful to the upstream contract); REST stays JSON.

## Consequences
- "Versioned event contracts" is an enforced registry gate plus contract tests, not a claim.
- One shared library prevents per-service DTO duplication.
- Costs accepted: Schema Registry container in compose (Phase 6+) and Avro codegen in the build.
- Rejected: JSON Schema (weaker codegen/compat tooling here; no reference precedent), schema-less JSON (unverifiable compatibility).
