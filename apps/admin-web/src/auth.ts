import NextAuth from "next-auth";
import type { JWT } from "next-auth/jwt";
import Keycloak from "next-auth/providers/keycloak";
import {
  decodeAccessToken,
  extractRoles,
  extractScopes,
} from "@/auth/claims";

/** Seconds of head-room before expiry at which we proactively refresh. */
const EXPIRY_SKEW_SECONDS = 30;
const TOKEN_TIMEOUT_MS = 10_000;

function issuer(): string {
  const value = process.env.AUTH_KEYCLOAK_ISSUER;
  if (!value) {
    throw new Error("AUTH_KEYCLOAK_ISSUER is required");
  }
  return value;
}

/**
 * Refresh the access token server-side using Keycloak's token endpoint.
 * admin-web is a public PKCE client, so no client secret is sent.
 */
async function refreshAccessToken(token: JWT): Promise<JWT> {
  if (!token.refreshToken) {
    return { ...token, error: "RefreshAccessTokenError" };
  }
  try {
    const response = await fetch(`${issuer()}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: process.env.AUTH_KEYCLOAK_ID ?? "admin-web",
        refresh_token: token.refreshToken,
      }),
      signal: AbortSignal.timeout(TOKEN_TIMEOUT_MS),
      cache: "no-store",
    });
    const refreshed = (await response.json()) as {
      access_token?: string;
      expires_in?: number;
      refresh_token?: string;
      id_token?: string;
    };
    if (!response.ok || !refreshed.access_token) {
      return { ...token, error: "RefreshAccessTokenError" };
    }
    const claims = decodeAccessToken(refreshed.access_token);
    return {
      ...token,
      accessToken: refreshed.access_token,
      expiresAt:
        Math.floor(Date.now() / 1000) + (refreshed.expires_in ?? 300),
      // Keycloak rotates refresh tokens; keep the old one if none returned.
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      idToken: refreshed.id_token ?? token.idToken,
      roles: extractRoles(claims),
      scopes: extractScopes(claims),
      username: claims.preferred_username ?? token.username,
      error: undefined,
    };
  } catch {
    return { ...token, error: "RefreshAccessTokenError" };
  }
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.AUTH_KEYCLOAK_ID ?? "admin-web",
      // Public client: Keycloak accepts token requests with auth method "none".
      issuer: process.env.AUTH_KEYCLOAK_ISSUER,
      client: { token_endpoint_auth_method: "none" },
      // Request only `openid`; the admin-web client's default scopes (roles, profile,
      // email, the resource scopes, and the service audiences) are applied by Keycloak
      // automatically. Requesting them explicitly trips Keycloak's invalid_scope check.
      authorization: { params: { scope: "openid" } },
    }),
  ],
  session: { strategy: "jwt" },
  pages: { signIn: "/login", error: "/login" },
  callbacks: {
    async jwt({ token, account }) {
      // Initial sign-in: capture the tokens Keycloak returned.
      if (account) {
        const claims = decodeAccessToken(account.access_token);
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
          roles: extractRoles(claims),
          scopes: extractScopes(claims),
          username: claims.preferred_username,
          error: undefined,
        };
      }
      // Still valid — reuse as-is.
      if (
        token.expiresAt &&
        Date.now() < (token.expiresAt - EXPIRY_SKEW_SECONDS) * 1000
      ) {
        return token;
      }
      // Expired — rotate.
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      session.roles = token.roles ?? [];
      session.scopes = token.scopes ?? [];
      session.user.username = token.username ?? null;
      session.user.name = token.username ?? session.user.name;
      session.error = token.error;
      // Access/refresh/id tokens are intentionally NOT attached to the session
      // (ADR-0010). Server code reads them from the encrypted JWT via getToken().
      return session;
    },
  },
});
