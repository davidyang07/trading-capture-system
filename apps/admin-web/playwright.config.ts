import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright runs against a locally running admin-web plus a live Keycloak (compose).
 *
 * Two tiers of specs:
 *  - Authentication/authorization specs (e2e/auth*.spec.ts) need only Keycloak + admin-web.
 *  - Full-stack data specs (e2e/*.full.spec.ts) additionally need the trading/account/
 *    enrichment services running; they self-skip unless E2E_FULL_STACK=1. Phase 12 brings the
 *    services up in compose (scripts/full-stack-up.ps1), so these now run against the real
 *    stack. This gates on infrastructure availability — it never disables a test to hide a defect.
 */
const PORT = Number(process.env.E2E_PORT ?? 3000);
const baseURL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [["list"]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL,
    trace: "on-first-retry",
    // Deterministic waits only — no arbitrary sleeps in specs.
    actionTimeout: 15_000,
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    // Invoke Next directly so Playwright owns and reliably tears down one server
    // process on Windows as well as POSIX hosts.
    command: "node node_modules/next/dist/bin/next dev",
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
