# ADR-0016: Trading-service ownership claim, 404 masking, and chain-amendment semantics

Status: Accepted (2026-07-14, Phase 3)

## Context
Phase 3 implementation forced four decisions the existing ADRs left open: which JWT claim
identifies an owner, how ownership denials surface over HTTP, exactly which executions are
amendable, and how allocation state interacts with amendments.

## Decision
1. **Owner subject = `preferred_username`, falling back to `sub`.** Keycloak generates user
   UUIDs (`sub`) at import time, so they differ across realm re-imports; usernames are the
   deterministic identity in the dev realm, the seed data, and the demo. The resolution rule
   lives in one place (`AccessContextFactory`) and never reads request content.
2. **Unowned resources answer 404, not 403.** The security rules require that resource
   existence is never leaked to non-owners; ownership denials therefore raise the same
   not-found error (and body) as missing resources. 403 is reserved for scope/role failures.
   The Phase 1 contract skeleton's "403 Not owner" is superseded by this refinement.
   The mask reports the **requested** resource type: an unowned execution GET answers
   `EXECUTION_NOT_FOUND`, identical to a genuinely missing execution — never
   `ORDER_NOT_FOUND`, which would reveal that the execution exists on someone else's order.
3. **Tip-only, cancel-terminal amendments.** Only the un-referenced end of a chain may be
   corrected or cancelled; a cancellation voids its whole chain and is itself immutable. The
   DB enforces one amendment per record (unique `exec_ref_id`), which with the tip rule makes
   branches and cycles impossible; `root_execution_id` is resolved at write time and persisted
   on every row (TRADE rows self-reference).
4. **Allocated executions are amendment-locked.** Corrections/cancellations of an execution
   currently bound to an allocation are rejected (409) until the allocation-engine releases
   it — protecting Phase 8's Replace/Cancel release semantics from racing amendments. The
   allocation pair (`allocation_state`, `allocation_id`) is the only mutable part of an
   execution row and is guarded by `@Version`.

## Consequences
- Deterministic seeds, demos, and ownership tests across realm re-imports; the trade-off
  (username reuse after deletion would inherit history) is acceptable for this system and
  documented.
- Investors cannot probe for other investors' order IDs; wrong-owner and unknown-resource
  read paths are indistinguishable (tested for both order and execution detail).
- **Accepted trade-off — order-create duplicate-ID oracle.** `POST /orders` rejects a
  client-supplied `orderId` that already exists with 409 `DUPLICATE_ORDER_ID` regardless of
  owner, so a caller could in principle test whether a *guessed* ID is taken. This is bounded
  and accepted: generated IDs are UUIDv4 (not enumerable), the endpoint is audit-logged, and
  the alternative (silently accepting a colliding ID, or per-owner ID namespaces) would break
  the global uniqueness that `exec_ref`/`root` resolution and cross-service references rely on.
  Read endpoints remain fully masked; only the write-time uniqueness check is observable.
- Execution history is provably append-only at three levels: service rules, DB constraints,
  and tests that assert original rows stay untouched.
- The execution-write endpoints additionally require the ADMIN role (capture is an
  operational function; investor tokens carry `execution:write` scope but not the role) —
  revisited in Phase 9's full scope-matrix hardening if the realm design changes.
