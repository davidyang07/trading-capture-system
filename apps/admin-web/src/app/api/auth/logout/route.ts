import { NextRequest, NextResponse } from "next/server";
import { getToken } from "next-auth/jwt";
import { signOut } from "@/auth";

// Federated logout must reach Keycloak with the id_token_hint, which lives only on the
// server-side JWT — so this handler runs on Node and reads it via getToken.
export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest): Promise<NextResponse> {
  const origin = req.nextUrl.origin;
  const secret = process.env.AUTH_SECRET;
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;

  let idToken: string | undefined;
  if (secret) {
    const secure = req.nextUrl.protocol === "https:";
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
    idToken = token?.idToken;
  }

  // Clear the local session cookie first.
  await signOut({ redirect: false });

  // Then end the Keycloak SSO session so a second login re-prompts.
  const postLogoutRedirect = `${origin}/login`;
  if (issuer) {
    const endSession = new URL(`${issuer}/protocol/openid-connect/logout`);
    endSession.searchParams.set("post_logout_redirect_uri", postLogoutRedirect);
    endSession.searchParams.set(
      "client_id",
      process.env.AUTH_KEYCLOAK_ID ?? "admin-web"
    );
    if (idToken) {
      endSession.searchParams.set("id_token_hint", idToken);
    }
    return NextResponse.redirect(endSession);
  }
  return NextResponse.redirect(postLogoutRedirect);
}
