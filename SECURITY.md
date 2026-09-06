# Security Model

Status: completed in Phase 9 (security hardening). Authoritative decision record: **ADR-0008**
(single Keycloak realm, OAuth2/OIDC), **ADR-0016** (trading ownership + 404 masking).
This document describes the **enforced** security behavior of the platform, not merely intended
policy. Every row of the authorization matrix below is exercised by an automated test.

## 1. Trust boundaries and identities

The platform is a set of Spring Security 6 OAuth2 **resource servers** that trust exactly one
issuer: the self-hosted Keycloak realm `tcs`. There is no API gateway (ADR-0009); each service
validates every token itself.

Two principal types exist:

| Identity | Obtained via | Carries | Example |
|---|---|---|---|
| **User** | Authorization Code + PKCE (browser, Auth.js BFF — ADR-0010) | realm role `INVESTOR` or `ADMIN`, user scopes, `preferred_username` | `investor1`, `tcs-admin` |
| **Machine** | Client Credentials (service account) | machine scopes only (no realm role) | `allocation-engine`, `trading-service` service accounts |

A **user** token is never accepted on a machine-only endpoint and a **machine** token never
carries a realm role, so it cannot reach role-gated user/admin endpoints. This separation is the
platform's primary defense against confused-deputy and privilege-escalation attacks.

## 2. Token validation (every resource server)

Shared in `libs/security-support` (`ResourceServerSecuritySupport`, `AudienceValidator`,
`KeycloakJwtAuthenticationConverter`); trading-service keeps equivalent local copies that predate
the library. Every service validates, on every request:

1. **Signature** — RS256 against the realm JWKS (discovered lazily from `issuer-uri`).
2. **Issuer** — must equal the realm issuer (`JwtValidators.createDefaultWithIssuer`).
3. **Expiry / not-before** — default Spring timestamp validator.
4. **Audience** — the token's `aud` claim must contain **this service's** audience
   (`AudienceValidator`). A token minted for one service is rejected by another. This is enforced
   in addition to a valid signature — a valid signature alone is never authorization.

Claim → authority mapping (`KeycloakJwtAuthenticationConverter`):

- `scope` (space-separated) → `SCOPE_<scope>` authorities.
- `realm_access.roles` → `ROLE_<role>` authorities.
- Client roles (`resource_access`) are deliberately **not** mapped — the realm defines only realm
  roles, so roles and scopes remain distinguishable authority namespaces and are never treated as
  interchangeable strings.

Any validation failure (missing/invalid/expired token, wrong issuer, wrong or missing audience,
malformed claims) results in **401** via the shared problem entry point. A valid token that lacks
a required scope or role results in **403** via the shared access-denied handler. Ownership denials
are masked as **404** (below).

## 3. Roles vs. scopes vs. audiences

- **Realm roles** (`ADMIN`, `INVESTOR`) express *who the user is*. Only user tokens carry them.
- **Client scopes** express *what an operation needs*. Both user and machine tokens carry scopes.
- **Audiences** (`trading-service`, `account-service`, `enrichment-service`, `allocation-engine`)
  express *which service a token is allowed to call*. Attached by dedicated `<service>-aud` client
  scopes with an `oidc-audience-mapper`.

Write operations that mutate global or privileged state require **both** a scope **and** the
`ADMIN` role (execution writes, enrichment reference-data mutations), so a scope leaked to a user
token is not by itself sufficient.

### Scope catalog

| Scope | Purpose | Granted to |
|---|---|---|
| `order:create/read/update/delete` | order CRUD | admin-web (users) |
| `execution:read` | read executions | admin-web |
| `execution:write` | capture/correct/cancel executions (also needs `ADMIN`) | admin-web, allocation-engine |
| `account:admin` | account lifecycle | admin-web |
| `account:balance` | post ledger entries, read balances | admin-web |
| `account:validate` | machine batch account validation (`/internal`) | trading-service, allocation-engine — **never** admin-web |
| `enrichment:read` | read reference data | admin-web, trading-service, allocation-engine |
| `enrichment:admin` | mutate reference data (also needs `ADMIN`) | admin-web |
| `allocation:process` | allocation-engine machine operations against trading/account/enrichment `/internal` | allocation-engine |

`account:validate` exists specifically so an investor's `account:balance` user token can **not**
reach the cross-owner batch endpoint (the Phase 4–5 disclosure fix).

## 4. Ownership / record-level authorization

Investor-owned records (orders, executions via their order, accounts, ledgers) are gated in the
**service layer** from the verified JWT's subject (`preferred_username`, else `sub`) — never from
request content or client-supplied identity headers.

**Policy: unauthorized ownership is masked as 404, not 403** (ADR-0016). A non-owner and a
non-existent id return an identical `*_NOT_FOUND` problem, so a caller cannot use the authorization
response to prove another investor's record exists (no existence oracle). `ADMIN` may see and
administer every record. Machine batch/internal endpoints operate cross-owner **by contract** and
are constrained by scope + audience instead of ownership.

## 5. Endpoint authorization matrix (enforced)

Principal: **U-INV** = investor user, **U-ADM** = admin user, **M** = machine (service account),
**public** = no token. Unauth response is **401**; insufficient-scope/role is **403**; unowned
record is **404**. All service (non-`/internal`, non-actuator) paths also require the service
audience — a wrong/missing audience is **401** before any scope check.

### trading-service (audience `trading-service`)

| Method | Path | Principal | Scope | Role | Ownership | In public OpenAPI |
|---|---|---|---|---|---|---|
| POST | `/api/v1/orders` | U-INV | `order:create` | — | owner = token subject | yes |
| GET | `/api/v1/orders` | U-INV/ADM | `order:read` | — | list filtered to owner (ADM: all) | yes |
| GET | `/api/v1/orders/{id}` | U-INV/ADM | `order:read` | — | 404-masked | yes |
| PUT | `/api/v1/orders/{id}` | U-INV/ADM | `order:update` | — | 404-masked, NEW-only | yes |
| DELETE | `/api/v1/orders/{id}` | U-INV/ADM | `order:delete` | — | 404-masked, NEW-only | yes |
| POST | `/api/v1/executions` | U-ADM | `execution:write` | `ADMIN` | — | yes |
| GET | `/api/v1/executions` | U-INV/ADM | `execution:read` | — | order-owner filtered | yes |
| GET | `/api/v1/executions/{id}` | U-INV/ADM | `execution:read` | — | 404-masked via order | yes |
| POST | `/api/v1/executions/{id}/correct` | U-ADM | `execution:write` | `ADMIN` | — | yes |
| POST | `/api/v1/executions/{id}/cancel` | U-ADM | `execution:write` | `ADMIN` | — | yes |
| POST | `/internal/v1/executions/resolve` | M | `allocation:process` | — | cross-owner by contract | yes (internal, machine) |
| PUT | `/internal/v1/executions/allocation-state` | M | `allocation:process` | — | cross-owner by contract | yes (internal, machine) |
| GET | `/actuator/health`, `/actuator/info` | public | — | — | — | n/a |
| GET | `/v3/api-docs/**`, `/swagger-ui/**` | public *(dev only)* | — | — | — | n/a |

Public only while springdoc is enabled. `EXPOSE_API_DOCS` drives both `springdoc.api-docs.enabled`
and the anonymous rule in each `SecurityConfig`; it defaults to `true` for local runs and the dev
stack, and the Kubernetes ConfigMaps set it to `false`, so a deployed service serves no API
contract to unauthenticated callers.

### account-service (audience `account-service`)

| Method | Path | Principal | Scope | Role | Ownership |
|---|---|---|---|---|---|
| POST | `/api/v1/accounts` | U-ADM/INV | `account:admin` | — | owner = subject (ADM may nominate) |
| GET | `/api/v1/accounts` | U-INV/ADM | `account:balance` | — | list filtered to owner (ADM: all) |
| GET | `/api/v1/accounts/{id}` | U-INV/ADM | `account:balance` | — | 404-masked |
| PUT | `/api/v1/accounts/{id}` | U-ADM | `account:admin` | — | 404-masked |
| POST | `/api/v1/accounts/{id}/{activate,suspend,close}` | U-ADM | `account:admin` | — | 404-masked |
| POST/GET | `/api/v1/accounts/{id}/cash-transactions`, `/security-transactions`, `/balances` | U-INV/ADM | `account:balance` | — | 404-masked |
| POST | `/internal/v1/accounts/batch` | M | `account:validate` \| `allocation:process` | — | cross-owner by contract |

### enrichment-service (audience `enrichment-service`)

| Method | Path | Principal | Scope | Role | Ownership |
|---|---|---|---|---|---|
| GET | `/api/v1/{instruments,exchanges,currencies,settlement-options,fee-rules}/**` (reads) | U-INV/ADM | `enrichment:read` | — | none (global reference data) |
| POST/PUT | reference-data mutations (create/update/activate/deactivate) | U-ADM | `enrichment:admin` | `ADMIN` | none |
| POST | `/internal/v1/instruments/lookup` | M / U | `enrichment:read` \| `allocation:process` | — | none |

The enrichment `/internal/v1/instruments/lookup` machine endpoint intentionally also accepts a
**user** `enrichment:read` token (principal **M / U**): it returns only global, non-owner reference
data that any `enrichment:read` holder can already read via `/api/v1`, so no owner-scoped data is
exposed and there is no escalation — the scope is shared by design, not an isolation gap. It still
rejects an unauthenticated request (401), which the live suite asserts.

### allocation-engine (audience `allocation-engine`)

Kafka-driven; exposes **no** inbound business HTTP endpoints. Only `/actuator/health` and
`/actuator/info` are public; any other path requires a valid token with audience `allocation-engine`.
No external caller has a legitimate reason to call it, so no client is granted `allocation-engine`
in its audience today (documented, intentional; a future inbound endpoint would add the scope).

## 6. Management / operational endpoints

Actuator exposure is `health,info` only across all services (verified by test:
`/actuator/env`, `/actuator/beans`, `/actuator/mappings` are **404** even to an admin token, and
**401** to an anonymous caller because the security chain rejects non-public paths first). Health
detail is not shown for anonymous callers. No env/config/bean/heap/thread/logger/shutdown endpoint
is reachable. `/actuator/prometheus` exposes metrics without JWT business access and is restricted
to in-namespace traffic by the Kubernetes NetworkPolicies.

## 7. CORS / CSRF / sessions

All services are **stateless** (`SessionCreationPolicy.STATELESS`, no server session). CSRF is
disabled because authentication is a bearer token, not a cookie/session — there is no ambient
authority for a forged cross-site request to ride on. Browser CORS is handled at the Auth.js BFF
(ADR-0010, Phase 10); the resource servers are same-origin-agnostic bearer APIs and do not enable
permissive CORS. Machine `/internal` endpoints are never called from a browser and expose no CORS.

## 8. Secrets and configuration

No production secrets exist in the repository. The realm export and `application*.yml` carry only
documented development placeholders (`*-dev-secret`, `*_dev`). Runtime secrets come from the
environment (`.env`, gitignored); `.env.example` documents the variables. Resource servers require
an issuer to be configured and fail closed if the realm is unreachable at token-validation time.

## 9. Logging and observability safety

Bearer tokens, `Authorization` headers, client secrets, passwords, and raw JWTs are never logged.
Remote-client failures are translated to safe, typed messages (`DependencyCallException` carries
only a safe message). Correlation IDs are validated (`[A-Za-z0-9._-]{1,64}`, else regenerated) to
prevent log injection, and are propagated but **not** used as metric tags. Security metrics are
low-cardinality counters (auth outcome, token-acquisition outcome) tagged only by
dependency/operation/outcome — never by subject, username, account/allocation/execution id, or raw
path variables.

## 10. Live verification

Live Keycloak-backed tests (`libs/test-support` `KeycloakTestContainer`) import this
exact realm; tests acquire **real** tokens): investor and admin user tokens (Authorization-Code
realm mappings proven via a dev-only direct-grant client), the `allocation-engine` service-account
client-credentials token, an insufficient-scope machine token, and a wrong-audience token. These
prove the realm's role/scope/audience mappings actually produce the authorities this model relies
on — not just that a hand-built token would validate.
