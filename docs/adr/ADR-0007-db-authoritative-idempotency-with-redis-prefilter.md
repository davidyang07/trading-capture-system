# ADR-0007: Idempotency is DB-authoritative with a Redis pre-filter

Status: Accepted (2026-07-14)

## Context
Kafka gives at-least-once delivery: broker redeliveries (same topic/partition/offset) and upstream producer retries (same business payload, new offset) both occur. The brief requires idempotent duplicate handling and forbids Redis as an authoritative store.

## Decision
Two authoritative unique constraints in MySQL: `raw_messages(topic, partition, offset)` absorbs broker redelivery; `allocations(alloc_id, alloc_trans_type)` absorbs business duplicates (a resent New/Replace/Cancel is recognized, acknowledged, and not reprocessed). A Redis `SETNX alloc:dedup:{allocId}:{transType}` (TTL 7 days) runs first as a cheap fast path. Redis being down or evicted only means the DB constraint does the work.

## Consequences
- The duplicate-delivery end-to-end scenario is provable by test at both layers.
- Redis gets a second legitimate job with an explicit expiration story, without ever being trusted for correctness.
- Dedup state lives in the same DB transaction as processing state — no separate consistency problem.
