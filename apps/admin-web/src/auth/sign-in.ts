import { cookies } from "next/headers";
import { signIn } from "@/auth";
import { isSessionCookie, SESSION_ENDED_COOKIE } from "@/auth/session-end";
import { sanitizeRelativePath } from "@/lib/redirect";

/**
 * Starts the Keycloak sign-in. Call it from a server action. It first drops the
 * session-ended marker and any session cookie a racing request re-created after logout, so
 * abandoning the identity-provider page cannot revive the old session.
 */
export async function beginSignIn(redirectTo: string): Promise<void> {
  const store = await cookies();
  for (const { name } of store.getAll()) {
    if (name === SESSION_ENDED_COOKIE || isSessionCookie(name)) {
      // Expire explicitly: a `__Secure-` cookie can only be overwritten with the Secure flag.
      store.set(name, "", { path: "/", maxAge: 0, secure: name.startsWith("__Secure-") });
    }
  }
  await signIn("keycloak", { redirectTo: sanitizeRelativePath(redirectTo) });
}
