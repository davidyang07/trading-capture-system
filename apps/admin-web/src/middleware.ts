import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { SESSION_ENDED_COOKIE } from "@/auth/session-end";

/**
 * Route protection derived from the encrypted session (ADR-0010) — never from a
 * build-time env role. This is a UX guard: the services independently enforce every
 * rule below. Hidden UI is not security.
 */

// Paths whose *actions* require the ADMIN realm role (execution writes and reference-data
// mutations, per SECURITY.md). Read-only lists remain available to investors.
const ADMIN_ONLY_PREFIXES = ["/executions/new", "/reference"];
const ADMIN_ONLY_SUFFIXES = ["/correct", "/cancel"];

function isAdminOnly(pathname: string): boolean {
  if (ADMIN_ONLY_PREFIXES.some((p) => pathname.startsWith(p))) {
    return true;
  }
  return ADMIN_ONLY_SUFFIXES.some((s) => pathname.endsWith(s));
}

export default auth((req) => {
  const { nextUrl } = req;
  const session = req.auth;
  const pathname = nextUrl.pathname;

  // The landing page is public; it sends signed-in users on to the dashboard itself.
  if (pathname === "/") {
    return NextResponse.next();
  }

  // After logout the session-ended marker outranks any session cookie a racing request re-set.
  const ended = req.cookies.has(SESSION_ENDED_COOKIE);
  if (!session || ended || session.error === "RefreshAccessTokenError") {
    const loginUrl = new URL("/login", nextUrl.origin);
    // Preserve the intended destination as a relative path only (open-redirect safe).
    loginUrl.searchParams.set("callbackUrl", pathname + nextUrl.search);
    return NextResponse.redirect(loginUrl);
  }

  const isAdmin = session.roles.includes("ADMIN");
  if (isAdminOnly(pathname) && !isAdmin) {
    return NextResponse.redirect(new URL("/403", nextUrl.origin));
  }

  return NextResponse.next();
});

export const config = {
  // Guard app pages; exclude API (self-enforcing), Auth.js, Next internals, the login
  // and 403 pages, and static assets. The public landing page (`/`) is let through above.
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|login|403|.*\\.(?:png|gif|ico|svg|jpg|jpeg|webp)$).*)",
  ],
};
