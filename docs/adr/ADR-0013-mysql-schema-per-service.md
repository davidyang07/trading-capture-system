# ADR-0013: One MySQL instance, schema-per-service, user-per-service

Status: Accepted (2026-07-14)

## Context
The brief requires service-owned databases or schemas with explicit ownership boundaries; local development must stay runnable on a laptop.

## Decision
One MySQL 8.4 container hosts four schemas (`tcs_trading`, `tcs_account`, `tcs_enrichment`, `tcs_allocation`). Each service connects with its own MySQL user granted privileges **only on its own schema** — cross-schema access is physically impossible, not merely conventional. Flyway runs per service against its schema.

## Consequences
- Database-ownership boundaries are enforced by grants and demonstrable in an interview.
- One container instead of four keeps compose light; the K8s story can still split instances later without app changes (connection strings only).
- Trade-off accepted: shared instance = shared failure domain locally — irrelevant for dev/demo, documented for production readers.
