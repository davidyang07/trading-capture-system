import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { ROUTES } from "./routes";

export default async function middleware_router_auth(request: NextRequest) {
  const role: any = process.env.APP_ROLE;
  const url = request?.nextUrl?.pathname ?? "";
  const _u0 = url.split("/").filter((p) => p)?.[0] ?? "";
  const valid = ROUTES.find((route) => {
    return route.url === `/${_u0}` && route.role.includes(role);
  });

  if (valid === null || valid === undefined) {
    return NextResponse.redirect(new URL("/403", request.url));
  } else {
    return NextResponse.next();
  }
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/execution/:path*",
    "/accounts/:path*",
    "/users/:path*",
  ],
};
