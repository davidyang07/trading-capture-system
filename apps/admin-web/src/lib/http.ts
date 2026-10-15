import { ApiError } from "@/lib/api-error";
import type { ServiceKey } from "@/server/services";

/**
 * Browser-side HTTP client. Every call targets the same-origin BFF proxy
 * (`/api/proxy/<service>/...`) which attaches the bearer token server-side (ADR-0010).
 * No backend URLs, no tokens, and no CORS live here.
 */

const PROXY_BASE = "/api/proxy";
const DEFAULT_TIMEOUT_MS = 15_000;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  /** Optional AbortSignal for request cancellation on navigation. */
  signal?: AbortSignal;
  /** Bounded client timeout; callers normally use the production default. */
  timeoutMs?: number;
}

function buildQuery(
  query?: RequestOptions["query"]
): string {
  if (!query) {
    return "";
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  }
  const s = params.toString();
  return s ? `?${s}` : "";
}

async function parseBody(response: Response): Promise<{
  value: unknown;
  empty: boolean;
  json: boolean;
}> {
  const text = await response.text();
  if (!text) {
    return { value: null, empty: true, json: false };
  }
  try {
    return { value: JSON.parse(text), empty: false, json: true };
  } catch {
    return { value: text, empty: false, json: false };
  }
}

const SESSION_ENDPOINT = "/api/auth/session";
// The proxy's answer when the session's access token has expired. The request never
// reached a service, so it can be retried once the session is refreshed.
const TOKEN_EXPIRED = "TOKEN_EXPIRED";

let sessionRefresh: Promise<void> | null = null;

/**
 * Reading the Auth.js session runs its jwt callback, which refreshes an expired access
 * token and re-issues the session cookie. Concurrent callers share one refresh.
 */
function refreshSession(): Promise<void> {
  if (!sessionRefresh) {
    sessionRefresh = fetch(SESSION_ENDPOINT, {
      cache: "no-store",
      credentials: "same-origin",
    })
      .then(() => undefined, () => undefined)
      .finally(() => {
        sessionRefresh = null;
      });
  }
  return sessionRefresh;
}

export async function apiFetch<T>(
  service: ServiceKey,
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  try {
    return await sendOnce<T>(service, path, options);
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.status === 401 &&
      error.errorCode === TOKEN_EXPIRED &&
      !options.signal?.aborted
    ) {
      await refreshSession();
      return sendOnce<T>(service, path, options);
    }
    throw error;
  }
}

async function sendOnce<T>(
  service: ServiceKey,
  path: string,
  options: RequestOptions
): Promise<T> {
  const {
    method = "GET",
    query,
    body,
    signal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
  } = options;
  const url = `${PROXY_BASE}/${service}/${path}${buildQuery(query)}`;
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  if (signal?.aborted) {
    controller.abort();
  } else {
    signal?.addEventListener("abort", abortFromCaller, { once: true });
  }
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, Math.max(1, timeoutMs));

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: {
        Accept: "application/json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
      cache: "no-store",
    });
  } catch (error) {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", abortFromCaller);
    if (signal?.aborted) {
      throw error;
    }
    if (timedOut || (error instanceof Error && error.name === "AbortError")) {
      throw ApiError.timeout();
    }
    throw ApiError.network();
  }

  try {
    const correlationId = response.headers.get("X-Correlation-ID");

    if (response.status === 204) {
      return undefined as T;
    }

    const parsed = await parseBody(response);

    if (!response.ok) {
      throw ApiError.fromResponse(response.status, correlationId, parsed.value);
    }

    // Every successful non-empty business response in the service contracts is JSON.
    // Treat an empty 2xx (other than 204) or non-JSON body as a protocol error rather
    // than silently manufacturing a successful value.
    if (parsed.empty || !parsed.json) {
      throw ApiError.malformed(response.status, correlationId ?? undefined);
    }

    return parsed.value as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (signal?.aborted) {
      throw error;
    }
    if (timedOut || (error instanceof Error && error.name === "AbortError")) {
      throw ApiError.timeout();
    }
    throw ApiError.network();
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", abortFromCaller);
  }
}
