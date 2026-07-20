import { NextRequest, NextResponse } from "next/server";
import { getToken } from "next-auth/jwt";
import { isServiceKey, serviceBaseUrl } from "@/server/services";

// The proxy attaches the bearer token and forwards to internal services, so it must run
// on the Node.js runtime (getToken + arbitrary outbound fetch, not the edge sandbox).
export const runtime = "nodejs";
// Never cache proxied, per-user, authorization-scoped responses.
export const dynamic = "force-dynamic";

const CORRELATION_HEADER = "X-Correlation-ID";
const CORRELATION_ID_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;
const UPSTREAM_TIMEOUT_MS = 15_000;
// Explicit allow-list prevents internal redirects, cookies, server details, and
// hop-by-hop headers from crossing the BFF boundary.
const FORWARDED_RESPONSE_HEADERS = new Set([
  "content-type",
  "retry-after",
  "x-correlation-id",
]);

function problem(
  status: number,
  errorCode: string,
  detail: string,
  correlationId?: string
) {
  const headers: Record<string, string> = {
    "content-type": "application/problem+json",
  };
  if (correlationId) {
    headers[CORRELATION_HEADER] = correlationId;
  }
  return NextResponse.json(
    { status, errorCode, detail, title: errorCode, correlationId },
    { status, headers }
  );
}

async function readAccessToken(req: NextRequest): Promise<{
  accessToken?: string;
  error?: string;
}> {
  const secret = process.env.AUTH_SECRET;
  if (!secret) {
    throw new Error("AUTH_SECRET is required");
  }
  const secure = new URL(req.url).protocol === "https:";
  const cookieName = secure
    ? "__Secure-authjs.session-token"
    : "authjs.session-token";
  const token = await getToken({
    req,
    secret,
    secureCookie: secure,
    cookieName,
    salt: cookieName,
  });
  return { accessToken: token?.accessToken, error: token?.error };
}

async function handle(
  req: NextRequest,
  ctx: { params: { path?: string[] } }
): Promise<NextResponse> {
  const segments = ctx.params.path ?? [];
  const [service, ...rest] = segments;

  if (!service || !isServiceKey(service)) {
    return problem(404, "NOT_FOUND", "Unknown proxy target");
  }

  // Defense-in-depth: the BFF only bridges the browser to documented REST APIs
  // (all under `api/...`). Management surfaces (actuator/management) stay off the
  // browser-reachable path even though the services also enforce authorization.
  if (rest[0] !== "api") {
    return problem(404, "NOT_FOUND", "Unknown proxy target");
  }

  const { accessToken, error } = await readAccessToken(req);
  if (error === "RefreshAccessTokenError" || !accessToken) {
    // Session missing or its refresh failed — force the browser to re-authenticate.
    return problem(401, "UNAUTHORIZED", "Not authenticated");
  }

  const requestedCorrelationId = req.headers.get(CORRELATION_HEADER);
  const correlationId =
    requestedCorrelationId && CORRELATION_ID_PATTERN.test(requestedCorrelationId)
      ? requestedCorrelationId
      : crypto.randomUUID();
  const outboundHeaders = new Headers();
  outboundHeaders.set("Authorization", `Bearer ${accessToken}`);
  outboundHeaders.set(CORRELATION_HEADER, correlationId);
  const contentType = req.headers.get("content-type");
  if (contentType) {
    outboundHeaders.set("content-type", contentType);
  }
  const accept = req.headers.get("accept");
  if (accept) {
    outboundHeaders.set("accept", accept);
  }

  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  let upstream: Response;
  const controller = new AbortController();
  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, UPSTREAM_TIMEOUT_MS);
  try {
    const safePath = rest.map((segment) => encodeURIComponent(segment)).join("/");
    const target = new URL(safePath, `${serviceBaseUrl(service)}/`);
    target.search = req.nextUrl.search;
    upstream = await fetch(target, {
      method: req.method,
      headers: outboundHeaders,
      body: hasBody ? await req.text() : undefined,
      redirect: "manual",
      cache: "no-store",
      signal: controller.signal,
    });
  } catch {
    // Do not leak internal host/URL details in the message.
    return problem(
      timedOut ? 504 : 502,
      timedOut ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNAVAILABLE",
      timedOut ? "Upstream service timed out" : "Upstream service unavailable",
      correlationId
    );
  } finally {
    clearTimeout(timeout);
  }

  const responseHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    if (FORWARDED_RESPONSE_HEADERS.has(key.toLowerCase())) {
      responseHeaders.set(key, value);
    }
  });
  responseHeaders.set(CORRELATION_HEADER, correlationId);

  const bodyText = await upstream.text();
  return new NextResponse(bodyText.length > 0 ? bodyText : null, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
