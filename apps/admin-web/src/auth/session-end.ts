/**
 * Logout hardening. Auth.js re-issues the JWT session cookie on every session read, so a request
 * that was already in flight when the user logged out (a route prefetch, say) can set the cookie
 * again after /api/auth/logout cleared it. Logout therefore also sets this marker, which no session
 * read ever writes: while it is present, any session cookie is treated as signed out. Signing in
 * again (src/auth/sign-in.ts) removes the marker together with any resurrected session cookie.
 *
 * Kept free of imports so the edge middleware can use it.
 */
export const SESSION_ENDED_COOKIE = "tcs.session-ended";

/** Outlives any access token a resurrected session could still carry (Keycloak: minutes). */
export const SESSION_ENDED_MAX_AGE_SECONDS = 24 * 60 * 60;

const SESSION_COOKIE_NAMES = [
  "authjs.session-token",
  "__Secure-authjs.session-token",
];

/** The Auth.js session cookie, including its chunks (`.0`, `.1`, …) when the JWT is large. */
export function isSessionCookie(name: string): boolean {
  return SESSION_COOKIE_NAMES.some(
    (base) => name === base || name.startsWith(`${base}.`)
  );
}
