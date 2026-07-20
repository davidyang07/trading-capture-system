/**
 * Server-side helpers for reading Keycloak access-token claims.
 *
 * These run only inside Auth.js callbacks (server/edge). The decoded payload is used
 * to derive UX-only role/scope awareness for the session — the resource servers remain
 * the authorization enforcement point (SECURITY.md). We never verify the signature here:
 * verification is the services' job; this is purely to surface claims the token already
 * proved when Keycloak issued it over the PKCE flow.
 */

export interface AccessTokenClaims {
  realm_access?: { roles?: string[] };
  scope?: string;
  preferred_username?: string;
}

/** Base64URL-decode the JWT payload segment without any external dependency (edge-safe). */
function base64UrlDecode(segment: string): string {
  const padded = segment.replace(/-/g, "+").replace(/_/g, "/");
  const withPadding = padded + "=".repeat((4 - (padded.length % 4)) % 4);
  // atob is available in both the edge and node runtimes Next.js uses.
  const binary = atob(withPadding);
  // Decode UTF-8 bytes so multi-byte usernames survive.
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export function decodeAccessToken(accessToken: string | undefined): AccessTokenClaims {
  if (!accessToken) {
    return {};
  }
  const parts = accessToken.split(".");
  if (parts.length < 2) {
    return {};
  }
  try {
    return JSON.parse(base64UrlDecode(parts[1])) as AccessTokenClaims;
  } catch {
    // A malformed token yields no claims rather than throwing inside the auth callback.
    return {};
  }
}

export function extractRoles(claims: AccessTokenClaims): string[] {
  return claims.realm_access?.roles ?? [];
}

export function extractScopes(claims: AccessTokenClaims): string[] {
  return claims.scope ? claims.scope.split(" ").filter(Boolean) : [];
}
