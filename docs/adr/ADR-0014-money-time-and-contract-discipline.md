# ADR-0014: Money, time, and API-contract discipline

Status: Accepted (2026-07-14)

## Context
The references store prices/quantities as `String`, statuses as bare `Integer`s, and mix `java.sql.Timestamp`/`ZonedDateTime`; entities leak toward controllers. The brief mandates BigDecimal, UTC-aware timestamps, and documented DTO boundaries.

## Decision
- `BigDecimal` ↔ `DECIMAL(19,4)` for money/quantities/fees; `DECIMAL(19,8)` for prices/rates. No float/double anywhere near money.
- `Instant` ↔ `DATETIME(6)` stored UTC (`connectionTimeZone=UTC`, `hibernate.jdbc.time_zone=UTC`).
- Enums persist as explicit string codes via JPA converters, preserving FIX code mappings (F/G/H, 0/1/2).
- Persistence entities are never API contracts: request/response DTOs (Java records preferred) per service with Bean Validation; mapping is explicit (hand-written mappers — no MapStruct dependency for four small services).
- New Java code uses records/constructor injection instead of Lombok (the references' Lombok usage is not carried forward).

## Consequences
- Financial correctness and timezone determinism by construction; enforced via `.claude/rules/` and reviewer agents.
- Slightly more mapping boilerplate — accepted for explicitness.
