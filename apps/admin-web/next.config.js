/** @type {import('next').NextConfig} */
// Minimal config for this project: React strict mode on, no ESLint suppression at build.
// Backend base URLs intentionally absent: Phase 10 replaces direct calls with the
// Auth.js BFF proxy (ADR-0010).

const dev = process.env.NODE_ENV !== "production";

// Everything the console loads comes from its own origin: the browser only ever talks to
// the BFF proxy, and fonts, icons and styles are bundled. Next's App Router writes inline
// scripts (the RSC payload) and PrimeReact sets inline styles, hence 'unsafe-inline'; the
// dev server additionally needs eval and a websocket for hot reload.
// form-action is left unset: sign-in and logout end in redirects to Keycloak, whose
// origin is deployment configuration rather than something fixed at build time.
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src 'self'${dev ? " ws: wss:" : ""}`,
  "object-src 'none'",
  "base-uri 'self'",
  "frame-ancestors 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  },
  // HSTS belongs to whatever terminates TLS (the ingress), not to a server that also
  // runs over plain http on localhost.
];

const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Standalone output is only needed for the Docker image (Phase 13), whose Linux
  // build layer can create the symlinks it requires. On Windows without Developer
  // Mode, the standalone trace step fails with EPERM — so it is opt-in via env.
  output: process.env.NEXT_OUTPUT_STANDALONE === "true" ? "standalone" : undefined,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

module.exports = nextConfig;
