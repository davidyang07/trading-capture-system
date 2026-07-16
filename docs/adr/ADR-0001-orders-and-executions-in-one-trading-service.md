# ADR-0001: Orders and executions live in one trading-service

Status: Accepted (2026-07-14)

## Context
Execution capture must atomically update order state (status, cumulative/leaves quantities), and correction/cancellation chains reference both orders and prior executions. The references kept them together in one backend (`deployment-tcs-exec-backend`).

## Decision
One `trading-service` owns orders and executions in one schema (`tcs_trading`), so the highest-frequency invariant (execution write ⇄ order state) is a local ACID transaction.

## Consequences
- No saga/outbox needed for the core trade-capture write path.
- Order and execution APIs share a deployment and scale together; acceptable at this system's scale and honest to state in interviews.
- Rejected alternative: separate order- and execution-services — would force distributed consistency for every fill with no compensating benefit, inflating the microservice count (explicitly banned by the project brief).
