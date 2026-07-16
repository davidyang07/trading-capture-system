# ADR-0008: Self-contained OAuth2/OIDC with a single Keycloak realm

Status: Accepted (2026-07-14)

## Context
Every reference depends on a course-hosted proprietary IdP ("Authsome") with binary-only SDKs — unusable and unreachable. The brief defaults to Keycloak and requires auth-code+PKCE, client-credentials M2M, granular scopes, roles, and ownership checks.

## Decision
One Keycloak realm `tcs` (exported JSON committed, image version pinned): public client `admin-web` (auth-code + PKCE, redirect to Auth.js callback), confidential service-account clients `allocation-engine`, `trading-service`, `account-service` (client-credentials). Realm roles `ADMIN`/`INVESTOR`; client scopes `order:create|read|update|delete`, `execution:read|write`, `account:admin`, `account:balance`, `enrichment:read|admin`, `allocation:process`. Every service is a Spring Security 6 resource server validating issuer + audience + scopes via realm JWKS; a shared converter maps `scope` claims and realm roles to authorities. Ownership (investor → own orders) is enforced in trading-service. Only documented dev credentials exist in the realm export.

## Consequences
- Fully self-contained local identity; Playwright can drive the real login flow.
- The reference scope model (`Authority.java`) survives as the scope taxonomy; the reference code (Spring Security 5, `WebSecurityConfigurerAdapter`) does not.
- Keycloak realm export is version-coupled — mitigated by pinning the image and re-exporting deliberately.
