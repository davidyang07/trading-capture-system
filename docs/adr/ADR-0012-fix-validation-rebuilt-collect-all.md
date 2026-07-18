# ADR-0012: FIX validation rebuilt as a collect-all policy validator

Status: Accepted (2026-07-14)

## Context
FIX message validation must accumulate **all** field errors rather than failing fast, and surface them through a typed error taxonomy — implemented here on public dependencies only.

## Decision
Build an allocation-engine validator that (1) runs QuickFIX/J `DataDictionary(fix44.xml)`-driven structural checks — required fields, repeating-group counts, field formats — by explicit iteration (QuickFIX/J's own `validate()` throws on first error, so it cannot be used directly for collect-all), and (2) applies hand-written policy rules (allowed AllocTransType/AllocType values, quantities > 0, parseable dates, group-level requirements), accumulating typed errors (MissingRequiredField, InvalidValueFormat, ValueNotSupported, ConflictingFields, …) with field names resolved via `DataDictionary.getFieldName`. All errors persist with the allocation; field-invalid messages are permanent failures (no retry).

## Consequences
- Uses public dependencies only, with unit-testable rules.
- The taxonomy feeds both `validation_errors` persistence and `ExceptionReported`/`AllocationRejected` payloads.
- Scope guard: validation targets AllocationInstruction (35=J) only — no generic multi-message framework.
