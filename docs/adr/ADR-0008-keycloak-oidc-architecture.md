# ADR-0008: Self-contained OAuth2/OIDC with a single Keycloak realm

Status: Accepted (2026-07-14)

## Context
The system requires a self-hosted, standards-based identity provider rather than any proprietary or hosted IdP. Keycloak is used, with auth-code + PKCE, client-credentials M2M, granular scopes, roles, and ownership checks.

## Decision
One Keycloak realm `tcs` (exported JSON committed, image version pinned): public client `admin-web` (auth-code + PKCE, redirect to Auth.js callback), confidential service-account clients `allocation-engine`, `trading-service`, `account-service` (client-credentials). Realm roles `ADMIN`/`INVESTOR`; client scopes `order:create|read|update|delete`, `execution:read|write`, `account:admin`, `account:balance`, `account:validate`, `enrichment:read|admin`, `allocation:process`. The dedicated `account:validate` scope is granted only to the allocation and trading machine clients, never to `admin-web`. Every service is a Spring Security 6 resource server validating issuer + audience + scopes via realm JWKS; a shared converter maps `scope` claims and realm roles to authorities. Cross-owner account batch validation requires `account:validate` or `allocation:process`, while enrichment mutations require both `enrichment:admin` and realm role `ADMIN`. Ownership (investor → own orders) is enforced in trading-service. Only documented dev credentials exist in the realm export.

## Consequences
- Fully self-contained local identity; Playwright can drive the real login flow.
- A scope-based authority taxonomy is retained; legacy Spring Security 5 (`WebSecurityConfigurerAdapter`) patterns are not used.
- Keycloak realm export is version-coupled — mitigated by pinning the image and re-exporting deliberately.
