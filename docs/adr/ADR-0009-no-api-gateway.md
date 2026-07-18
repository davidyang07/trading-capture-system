# ADR-0009: No API gateway

Status: Accepted (2026-07-14)

## Context
A full gateway stack (Spring Cloud Gateway + Eureka + Config Server) was considered. Guiding principle: no gateway unless justified, and no inflated service counts.

## Decision
No gateway. The Next.js BFF is the single browser-facing entry point (session termination + token relay); services are called directly by the BFF and by machine clients; Kubernetes Ingress performs path routing in-cluster.

## Consequences
- One less hop, one less container, one less failure mode; local startup stays simple.
- Cross-cutting concerns land where they belong at this scale: authN/Z in Keycloak + resource servers, correlation in `observability-support`, rate limiting out of scope.
- Documented threshold for revisiting: many first-party API consumers, centralized rate limiting/API keys, or protocol translation needs — at that point Spring Cloud Gateway is the natural fit (and Eureka/Config Server remain excluded regardless; compose/K8s provide discovery and config).
