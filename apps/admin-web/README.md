# admin-web — Trading Capture System frontend

A Next.js 15 (App Router) + TypeScript operator/investor application over the Trading
Capture System services. It renders order, execution, and account workflows with role-aware
navigation, resilient loading/empty/error states, and WCAG 2.1 AA-minded accessibility.

## Architecture

```
route/page (server + client components)
  → feature components
    → query/mutation hooks (useQuery / useMutation)
      → typed API client (lib/http → same-origin BFF)
        → BFF proxy route handler (attaches bearer server-side)
          → trading / account / enrichment services
```

- **Authentication (ADR-0010):** Auth.js (next-auth v5) with the Keycloak provider using
  Authorization Code + PKCE. Access/refresh/id tokens live **only** in the encrypted,
  httpOnly session cookie (server side). They are never exposed to browser JavaScript, never
  put in the client `Session`, never logged, and never placed in the DOM or query strings.
- **BFF proxy:** `src/app/api/proxy/[...path]/route.ts` (Node runtime) reads the access token
  from the encrypted session via `getToken`, attaches `Authorization: Bearer …`, forwards a
  correlation id, and proxies to the target service. The browser only ever calls same-origin
  `/api/proxy/<service>/…` — no backend URLs or CORS in the browser.
- **Session refresh:** the Auth.js `jwt` callback refreshes the access token against Keycloak
  before expiry; a failed refresh marks the session `RefreshAccessTokenError`, and the proxy
  returns 401 so the UI re-authenticates.
- **Federated logout:** `GET /api/auth/logout` clears the session cookie and redirects to the
  Keycloak end-session endpoint (with `id_token_hint`), then back to `/login`.
- **Authorization is UX-only here.** Role-aware nav and route guards (`middleware.ts`) improve
  UX; the services enforce every scope/role/ownership rule (see `../../SECURITY.md`).

## Routes

| Route | Purpose | Access |
|---|---|---|
| `/login` | Keycloak sign-in (Auth Code + PKCE) | public |
| `/dashboard` | Role-aware activity summary | authenticated |
| `/orders`, `/orders/new`, `/orders/[orderId]` | Order list, create, detail + edit/delete (NEW-only) | authenticated |
| `/executions`, `/executions/[internalId]` | Execution list + detail with amendment chain | authenticated (read) |
| `/executions/new` | Capture a trade fill | **ADMIN** |
| `/executions/[internalId]/correct`, `/cancel` | Append correction / cancellation | **ADMIN** |
| `/accounts`, `/accounts/new`, `/accounts/[accountId]` | Accounts, balances, cash/security ledgers; lifecycle actions and posting transactions are ADMIN-only | authenticated (owned) |
| `/reference` | Instruments / currencies / exchanges (read-only) | **ADMIN** |
| `/403` | Authorization-denied page | — |

## API client & error model

- `lib/http.ts` — typed `apiFetch<T>(service, path, opts)` against the BFF; cancellation via
  `AbortSignal`; empty-body (204) handling; never treats a non-2xx as empty success.
- `lib/api-error.ts` — `ApiError` with a typed `kind` (validation / unauthenticated /
  unauthorized / not-found / conflict / rate-limited / server / network / malformed). Screens
  render a **safe** `userMessage` and keep a correlation id for support; raw server detail and
  stack traces are never shown.
- `types/api.ts` — DTOs aligned with `contracts/openapi/*.yaml`. Decimal money/quantity fields
  are kept as **strings** end-to-end (ADR-0014) — never routed through JS floating point.

## Environment configuration

Copy `.env.example` to `.env.local` (gitignored). All variables are **server-only** (no
`NEXT_PUBLIC_` — the browser never needs them):

| Variable | Purpose |
|---|---|
| `AUTH_SECRET` | Encrypts the session cookie (per-machine dev value) |
| `AUTH_KEYCLOAK_ID` | Public client id (`admin-web`) |
| `AUTH_KEYCLOAK_ISSUER` | `http://localhost:8180/realms/tcs` |
| `TRADING_SERVICE_URL` / `ACCOUNT_SERVICE_URL` / `ENRICHMENT_SERVICE_URL` | BFF proxy targets |

No client secret is used (public PKCE client). No secret is ever shipped to the browser.

## Local development

```powershell
corepack pnpm install
# Bring up Keycloak (and the services you need) via the root docker-compose:
docker compose up -d keycloak
corepack pnpm --filter admin-web dev   # http://localhost:3000
```

> First-time Keycloak: if the realm was imported by an older build, wipe the volume so the
> current realm (with the `roles`/`profile`/`email` and service-audience client scopes) is
> re-imported: `docker compose rm -sf keycloak && docker volume rm tcs_keycloak-data && docker compose up -d keycloak`.

## Testing

```powershell
corepack pnpm --filter admin-web lint
corepack pnpm --filter admin-web typecheck
corepack pnpm --filter admin-web test          # jest + Testing Library (+ jest-axe)
corepack pnpm --filter admin-web build
corepack pnpm --filter admin-web exec playwright install chromium
corepack pnpm --filter admin-web test:e2e      # Playwright (needs Keycloak up)
```

- **Unit/component (jest + Testing Library):** format/decimal handling, validation, API error
  mapping, permissions, claims decoding, the HTTP client, `useMutation` duplicate-submit guard,
  role-aware `Nav`, `Header` logout, accessible form fields, loading/empty/error states, and
  the order form. Accessibility is asserted with `jest-axe` on our own semantic markup.
- **E2E (Playwright):** the default suite drives the **real** Keycloak PKCE login through the
  BFF, then uses deterministic per-test BFF network fixtures for downstream UI behavior. It
  covers unauthenticated/expired sessions, investor/admin sign-in, logout, role denial,
  open-redirect defense, responsive shell behavior, owned/cross-owner resources, balances and
  positions, exact decimal submission, duplicate-submit prevention, business rejection,
  administrator lifecycle/validation workflows, safe unauthorized/network/malformed states,
  retry, and correlation references. The fixtures isolate UI behavior without weakening service
  authorization, which is independently integration-tested by the backend modules.
- **Live data-flow E2E:** `e2e/*.full.spec.ts` covers seeded order/execution/account operations
  through real services and is gated by `E2E_FULL_STACK=1`. These specs additionally need the
  containerized trading/account/enrichment services started by `scripts/full-stack-up.ps1 -Build`.
  The gate is infrastructure-explicit; default tests do not share or mutate backend data.

## Design system

[`DESIGN.md`](DESIGN.md) is the visual spec: a Stripe-derived look (indigo `#533afd`, navy ink,
Inter, pill buttons, hairline panels on a soft canvas) with IBM Carbon's data-table rules
(40px rows, one line per row, right-aligned tabular numbers, pagination bottom-right). Its
front matter holds the tokens; `src/style/global.scss` mirrors them as `--tcs-*` CSS
variables, and `src/style/primereact.scss` maps PrimeReact (Lara indigo) onto them. Change
a token in `DESIGN.md` first, then in `global.scss`, and lint the spec with
`npx @google/design.md lint DESIGN.md`.

## Accessibility

Semantic landmarks, a skip link, one `<h1>` per page, labelled inputs with
`aria-describedby` error/help wiring, `role="alert"` errors and `role="status"` live regions,
color-independent status badges (glyph + text), visible focus, keyboard-operable menus, and a
`prefers-reduced-motion` guard. Automated checks (jest-axe) plus manual keyboard reasoning.

## Current operational boundary

Backend correlation/metrics, coverage gates, resilience dashboards, and containerized full-stack
Playwright flows are implemented. Browser real-user monitoring and distributed tracing are not
part of the current observability scope. This app deliberately does not implement allocation-engine
query views because that service exposes no public query API.
