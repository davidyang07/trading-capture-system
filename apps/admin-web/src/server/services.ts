import "server-only";

/**
 * Server-only registry mapping the BFF's `/api/proxy/<service>/...` prefix to the
 * downstream service base URL. These URLs are server-only environment variables —
 * they must never be exposed with a NEXT_PUBLIC_ prefix (they are internal to the
 * BFF and, in compose/k8s, resolve to in-cluster hostnames).
 */
export type ServiceKey = "trading" | "account" | "enrichment";

const ENV_BY_SERVICE: Record<ServiceKey, string> = {
  trading: "TRADING_SERVICE_URL",
  account: "ACCOUNT_SERVICE_URL",
  enrichment: "ENRICHMENT_SERVICE_URL",
};

export function isServiceKey(value: string): value is ServiceKey {
  return value === "trading" || value === "account" || value === "enrichment";
}

export function serviceBaseUrl(service: ServiceKey): string {
  const envName = ENV_BY_SERVICE[service];
  const base = process.env[envName];
  if (!base) {
    throw new Error(`Missing service URL env var ${envName}`);
  }
  const parsed = new URL(base);
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error(`Invalid protocol for ${envName}`);
  }
  parsed.pathname = parsed.pathname.replace(/\/+$/, "");
  parsed.search = "";
  parsed.hash = "";
  return parsed.toString().replace(/\/$/, "");
}
