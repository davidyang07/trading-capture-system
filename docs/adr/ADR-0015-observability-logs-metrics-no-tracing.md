# ADR-0015: Observability = structured logs + correlation IDs + Prometheus metrics; no distributed tracing

Status: Accepted (2026-07-14)

## Context
The references contain a genuinely good structured-logging/MDC library (`audit-utils`) but zero metrics stack. The brief requires JSON logs, correlation IDs, health checks, Micrometer, Prometheus, and Grafana — and bans features added only to appear impressive.

## Decision
`libs/observability-support` (ported/modernized from `audit-utils` source) provides: a correlation-ID servlet filter (accepts or generates `X-Correlation-ID`, echoes it back), MDC task decoration for async hops, Kafka header propagation helpers (later phase), and a shared Logstash-encoder logback include. Every service exposes Actuator health (liveness/readiness) and `/actuator/prometheus`; business metrics (order/execution creations, allocation outcomes + duration, outbox backlog, cache hit/miss) are Micrometer counters/timers/gauges. Prometheus scrapes; Grafana ships two provisioned dashboards (service/JVM + allocation business). Distributed tracing (OpenTelemetry) is explicitly out of scope: correlation IDs already answer "what happened to this request" across the demo, and a half-configured tracing stack would violate the honesty rule. Documented as future work.

## Consequences
- Every observability claim is demonstrable in the local stack.
- Kafka consumer lag is reported only if a lag exporter lands cheaply in Phase 11; otherwise recorded as a known limitation (spring-kafka listener metrics as partial substitute).
