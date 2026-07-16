# ADR-0003: enrichment-service is a separate read-mostly reference-data service

Status: Accepted (2026-07-14)

## Context
Instruments, alternate identifiers, exchanges, currencies, settlement options, and fee rules are consumed by trading-service and allocation-engine. In the references this was a remote course-hosted service; the only local module was an empty stub, so the service must be built new.

## Decision
`enrichment-service` owns reference data in `tcs_enrichment` with deterministic Flyway seeds, lookup/search/validation APIs, and Redis read-through caching (15-min TTL, eviction on admin mutation, graceful degradation to MySQL).

## Consequences
- Redis has a legitimate, measurable job (cache hit/miss metrics) instead of being résumé decoration.
- Fee calculation stays non-intrusive: consumers apply fee rules only when incoming data carries no fees.
- Seeded records replace the course's hardcoded/hosted data — deterministic for tests and demos.
