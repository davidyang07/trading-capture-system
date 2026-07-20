import Image from "next/image";
import { redirect } from "next/navigation";
import { auth, signIn } from "@/auth";
import { sanitizeRelativePath } from "@/lib/redirect";

export const metadata = { title: "Sign in · Trading Capture System" };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: { callbackUrl?: string; error?: string };
}) {
  const callbackUrl = sanitizeRelativePath(searchParams.callbackUrl);

  // Already authenticated with a healthy session — go straight in.
  const session = await auth();
  if (session && !session.error) {
    redirect(callbackUrl);
  }

  async function login() {
    "use server";
    await signIn("keycloak", { redirectTo: callbackUrl });
  }

  const expired = searchParams.error || session?.error;

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <Image
          src="/logo.png"
          alt=""
          width={64}
          height={64}
          priority
          className="login-logo"
        />
        <h1 id="login-title">Trading Capture System</h1>
        <p className="login-subtitle">Distributed Trading Platform</p>
        {expired ? (
          <p className="login-alert" role="alert">
            Your session has ended. Please sign in again.
          </p>
        ) : null}
        <form action={login}>
          <button type="submit" className="login-button">
            <i className="pi pi-sign-in" aria-hidden="true" /> Sign in with
            Keycloak
          </button>
        </form>
        <p className="login-hint">
          You will be redirected to the identity provider to sign in securely.
        </p>
      </section>
    </main>
  );
}
