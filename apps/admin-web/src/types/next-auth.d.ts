import type { DefaultSession } from "next-auth";

/**
 * Session/JWT augmentation.
 *
 * The client-visible `Session` carries only UX-safe claims (roles, scopes, username).
 * Access/refresh/id tokens live exclusively on the encrypted server-side `JWT` and are
 * never copied into the session (ADR-0010 — tokens never reach the browser).
 */
declare module "next-auth" {
  interface Session {
    roles: string[];
    scopes: string[];
    error?: "RefreshAccessTokenError";
    user: {
      username?: string | null;
    } & DefaultSession["user"];
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
    expiresAt?: number;
    roles?: string[];
    scopes?: string[];
    username?: string;
    error?: "RefreshAccessTokenError";
  }
}
