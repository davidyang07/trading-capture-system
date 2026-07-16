# ADR-0010: Frontend sessions via Auth.js + BFF token relay (no tokens in the browser)

Status: Accepted (2026-07-14)

## Context
The reference frontend stores the access token in `localStorage` (XSS-exfiltratable), leaves 401 handling commented out, and fakes authorization with a build-time `APP_ROLE` env split. The brief requires auth-code+PKCE and bans insecure token handling.

## Decision
Auth.js (next-auth v5) with the Keycloak provider handles login/callback/logout and keeps access+refresh tokens in an encrypted httpOnly session cookie (JWT session strategy, server-side only). Next.js route handlers act as a BFF proxy: browser calls `/api/proxy/...` with its cookie; the handler attaches the bearer token and forwards to the target service; refresh-token rotation happens server-side. Tokens never reach browser JavaScript. UI permission-awareness reads role/scope claims from the session — as UX only; services remain the enforcement point.

## Consequences
- Real PKCE flow drivable end-to-end in Playwright against local Keycloak.
- The axios layer points at same-origin BFF routes — CORS complexity disappears.
- Costs accepted: a thin proxy layer to maintain; SSR/route handlers must forward correlation headers.
- Rejected: oidc-client-ts in-browser tokens (repeats the localStorage class of problems), custom cookie code (re-implementing Auth.js), keeping the reference flow (insecure).
