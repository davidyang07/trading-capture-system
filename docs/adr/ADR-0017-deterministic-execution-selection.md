# ADR-0017: Deterministic FIFO execution selection

Status: Accepted (2026-07-17)

## Context
Phase 8 orchestration must choose which resolved executions back an allocation's requested quantity.
The choice must be deterministic (identical snapshot ⇒ identical output, independent of input/collection
order), correct with `BigDecimal` money/quantity scale, and safe under concurrency (two orchestrations
must never over-consume the same execution quantity).

## Decision
`ExecutionSelector` selects over a framework-free snapshot (`SelectionRequest` of `SelectionCandidate`s
projected from the trading resolution results) and returns a typed `SelectionResult`.

- **Eligibility:** a candidate is eligible iff it is `eligible` (trading verdict ELIGIBLE), its
  `instrumentId` and `side` match the allocation, and its `allocatableQuantity > 0`. Non-matching or
  zero-quantity candidates are silently excluded; cancelled/superseded/already-allocated are already
  non-`eligible`.
- **Total order (FIFO):** ascending execution `transactTime`, tie-broken by `execId`, then
  `internalId`. This is a strict total order over distinct executions, independent of input order and
  of any hash/DB/locale ordering.
- **Greedy exact fill:** consume earliest-first, taking `min(remaining, allocatable)` from each until
  the requested quantity is met. The last consumed execution may be partially taken. The selected
  quantities sum **exactly** to the requested quantity.
- **No partial allocation:** if the aggregate eligible quantity is below the request, the result is
  `Insufficient` — never a partial success.
- **Decimals:** scale 4 (ADR-0014), `RoundingMode.UNNECESSARY` (no rounding — takes are exact
  subtractions); requested/candidate quantities with scale > 4 are rejected as `InvalidCandidate`.
  No floating-point arithmetic anywhere.
- **Result variants:** `Complete`, `Insufficient`, `NoEligibleExecutions`, `InvalidCandidate`
  (malformed candidate/request data), `Invariant` (impossible internal state).

## Concurrency boundary
Selection is a pure calculation over a **snapshot** and does not reserve quantity. The New/Replace
mutation transaction re-reads authoritative database state under locking (`@Version` optimistic
checks on the allocation, plus the trading allocation-state API's own conflict handling) before
persisting assignments. A stale selection therefore fails safely at the mutation boundary rather than
over-allocating, and retries recompute selection against fresh state.

## Consequences
- Deterministic, auditable selection with an explicit sequence and before/after availability per pick.
- Correctness (exact decimals, no over-allocation) is prioritized over throughput optimization.
- Rejected: quantity-proportional or price-optimizing selection (not required by the allocation
  contract, non-deterministic without extra tie-breakers), and reserving quantity during selection
  (would need a distributed lock across services — out of scope; the DB is the authority).
