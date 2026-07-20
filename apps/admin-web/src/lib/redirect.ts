/**
 * Open-redirect guard. Only same-origin relative paths are allowed as post-login
 * destinations; anything else falls back to the default. Rejects protocol-relative
 * (`//host`), absolute URLs, and backslash tricks.
 */
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
  return candidate;
}
