/** @type {import('next').NextConfig} */
// Minimal config for this project: React strict mode on, no ESLint suppression at build.
// Backend base URLs intentionally absent: Phase 10 replaces direct calls with the
// Auth.js BFF proxy (ADR-0010).
const nextConfig = {
  reactStrictMode: true,
  // Standalone output is only needed for the Docker image (Phase 13), whose Linux
  // build layer can create the symlinks it requires. On Windows without Developer
  // Mode, the standalone trace step fails with EPERM — so it is opt-in via env.
  output: process.env.NEXT_OUTPUT_STANDALONE === "true" ? "standalone" : undefined,
};

module.exports = nextConfig;
