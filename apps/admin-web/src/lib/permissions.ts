import type { Session } from "next-auth";

/**
 * UX-only permission helpers derived from session claims. These decide what to *show*;
 * the services enforce every rule server-side (SECURITY.md). Hidden UI is never security.
 */
export function isAdmin(session: Pick<Session, "roles"> | null): boolean {
  return session?.roles?.includes("ADMIN") ?? false;
}

export function isInvestor(session: Pick<Session, "roles"> | null): boolean {
  return session?.roles?.includes("INVESTOR") ?? false;
}

/** Execution capture/correct/cancel require the ADMIN role in addition to scope. */
export function canWriteExecutions(
  session: Pick<Session, "roles"> | null
): boolean {
  return isAdmin(session);
}

/** Reference-data mutations require the ADMIN role in addition to scope. */
export function canManageReferenceData(
  session: Pick<Session, "roles"> | null
): boolean {
  return isAdmin(session);
}
