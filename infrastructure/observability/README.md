# Observability (Phase 11)

Structured JSON logging + correlation IDs live in `libs/observability-support` (Phase 2+). Phase 11
adds the metrics stack: Micrometer → Prometheus → Grafana, per ADR-0015. No distributed tracing —
correlation IDs answer "what happened to this request" across the demo.

## What each service exposes

Every service brings `spring-boot-starter-actuator` + `micrometer-registry-prometheus` transitively
through `observability-support` and exposes:

- `/actuator/health`, `/actuator/health/{liveness,readiness}`, `/actuator/info` — probes.
- `/actuator/prometheus` — the scrape endpoint (a documented public metrics path; it exposes only
  meters, never business data — `env`/`beans`/etc. stay locked behind the JWT). In production this
  would be network-restricted (Phase 13 K8s NetworkPolicy) or on a separate management port.

Every meter carries a common `application=<spring.application.name>` tag (set in
`ObservabilitySupportAutoConfiguration`) so Prometheus/Grafana can slice by service.

### Business metrics (already registered in earlier phases, scraped from Phase 11)

- trading-service: `tcs.trading.orders.{created,updated,deleted}`,
  `tcs.trading.executions.recorded{execType}`, `tcs.trading.allocation_state.applied{action}`.
- account-service: `AccountMetrics` counters.
- enrichment-service: `tcs.enrichment.cache.{hit,miss,error,eviction}`, reference mutations.
- allocation-engine: `tcs.allocation.ingest.*` (consumed/accepted/rejected/dead_lettered/…),
  `tcs.allocation.outbox.backlog` **gauge** (unpublished outbox rows — new in Phase 11).

Prometheus renames dotted meters (`tcs.allocation.outbox.backlog` → `tcs_allocation_outbox_backlog`;
counters gain `_total`). The dashboards use the Prometheus names.

## Prometheus

`prometheus/prometheus.yml` scrapes all four services every 15s. For the host-run workflow the
targets are `host.docker.internal:8081-8084`. In the **Phase 12 full stack**
(`docker-compose.full.yml`) the services join the compose network, so the override swaps in
`prometheus/prometheus.full.yml`, which scrapes them by DNS (`trading-service:8081`, …).

## Grafana

Provisioned (no manual clicks) from `grafana/provisioning`:

- datasource `Prometheus` (uid `tcs-prometheus`, default) → `http://prometheus:9090`.
- two dashboards from `grafana/dashboards`:
  - **TCS · Service & JVM** (`tcs-service-jvm`): per-service up, HTTP req rate + p95 latency,
    JVM heap, process CPU.
  - **TCS · Allocation & Trading Business** (`tcs-allocation-business`): outbox backlog,
    allocations accepted, orders created, ingestion outcome rates, executions by type,
    enrichment cache hit/miss.

## Run it

```powershell
# from the repo root
docker compose up -d prometheus grafana
# then start the services on the host (mvn spring-boot:run per service) so Prometheus can scrape them
```

- Prometheus UI: http://localhost:9090 (Status → Targets shows the four services UP once running).
- Grafana: http://localhost:3001 (admin / `grafana_admin_dev` by default; override via env).

## JSON logging

`json-logs` Spring profile switches every service to the Logstash-encoder console appender (each
`logback-spring.xml` sets `SERVICE_NAME` and includes `logback-tcs.xml`). Activate with
`SPRING_PROFILES_ACTIVE=json-logs`; the default profile stays human-readable with the correlation id.

## Ports (see `.env.example`)

`PROMETHEUS_HOST_PORT` 9090, `GRAFANA_HOST_PORT` 3001, `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD`
(local dev defaults only).
