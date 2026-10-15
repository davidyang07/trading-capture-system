/**
 * Open-redirect guard. Only same-origin relative paths are allowed as post-login
 * destinations; anything else falls back to the default. Rejects protocol-relative
 * (`//host`), absolute URLs, and backslash tricks.
 *
 * Control characters and whitespace are rejected too: browsers strip tabs and newlines
 * from URLs, so `/\t/evil.example` would otherwise become `//evil.example`. As a final
 * check the path is resolved the way a browser would and must stay on the same origin.
 */
const BASE = "http://redirect-check.invalid";

export function sanitizeRelativePath(
  candidate: string | undefined | null,
  fallback = "/dashboard"
): string {
  if (!candidate) {
    return fallback;
  }
  if (!candidate.startsWith("/")) {
    return fallback;
  }
  if (candidate.startsWith("//") || candidate.startsWith("/\\")) {
    return fallback;
  }
  if (candidate.includes("\\")) {
    return fallback;
  }
  // eslint-disable-next-line no-control-regex
  if (/[\u0000- \u007f-\u009f]/.test(candidate)) {
    return fallback;
  }
  try {
    const resolved = new URL(candidate, BASE);
    if (resolved.origin !== BASE) {
      return fallback;
    }
  } catch {
    return fallback;
  }
  return candidate;
}
