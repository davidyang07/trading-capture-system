import type { Metadata } from "next";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { SESSION_ENDED_COOKIE } from "@/auth/session-end";
import { beginSignIn } from "@/auth/sign-in";
import { Landing } from "@/components/Landing/Landing";

export const metadata: Metadata = {
  title: "Trading Capture System · Post-trade operations console",
  description:
    "Orders, executions, accounts and FIX allocations in one console, backed by an immutable ledger and idempotent event processing.",
};

/**
 * Public front door. Signed-in users go straight to their dashboard, as `/` always did;
 * everyone else sees the landing page, whose buttons start the Keycloak sign-in directly.
 */
export default async function HomePage() {
  const session = await auth();
  const ended = (await cookies()).has(SESSION_ENDED_COOKIE);
  if (session && !session.error && !ended) {
    redirect("/dashboard");
  }

  async function signInAction() {
    "use server";
    await beginSignIn("/dashboard");
  }

  return <Landing signInAction={signInAction} />;
}
