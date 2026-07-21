# Trading Capture System — hardened admin-web delivery image.
# Multi-stage Next.js standalone build, pnpm via corepack, DIGEST-PINNED base, non-root numeric
# UID (K8s runAsNonRoot), OCI labels, HEALTHCHECK. Scanned with trivy in CI.
#
# Build context is the repository root (pnpm workspace: lockfile + workspace manifest live
# there); admin-web has no workspace-internal dependencies. Example:
#   docker build -f apps/admin-web/Dockerfile -t tcs/admin-web:local .
#
# Standalone output is enabled here (NEXT_OUTPUT_STANDALONE=true); the Linux build layer can
# create the file-trace symlinks that Windows without Developer Mode cannot (see next.config.js).

# node:22-alpine (pinned to the multi-arch manifest digest for reproducibility).
FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS base
RUN corepack enable
WORKDIR /repo

# --- deps: install with the frozen lockfile, admin-web subset only ---------------------
FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin-web/package.json ./apps/admin-web/package.json
RUN pnpm install --frozen-lockfile --filter admin-web...

# --- build: produce the standalone server ----------------------------------------------
FROM base AS build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/apps/admin-web/node_modules ./apps/admin-web/node_modules
COPY apps/admin-web ./apps/admin-web
ENV NEXT_OUTPUT_STANDALONE=true \
    NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter admin-web build

# --- runtime: standalone bundle, non-root ----------------------------------------------
# Start from the pinned Node image again instead of the build-tool base. The standalone server
# needs Node only; npm/corepack and their transitive package managers are unnecessary runtime
# attack surface and are removed before the application is copied.
FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS runtime
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
 && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack /usr/local/bin/pnpm \
           /usr/local/bin/pnpx /usr/local/bin/yarn /usr/local/bin/yarnpkg \
 && addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 nextjs
WORKDIR /app
# The monorepo standalone output mirrors the repo layout under apps/admin-web/.
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/.next/static ./apps/admin-web/.next/static
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/public ./apps/admin-web/public
USER 1001:1001
EXPOSE 3000
# Auth.js exposes /api/auth/session as an unauthenticated 200 — a dependency-free readiness probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD wget -q -O /dev/null http://localhost:3000/api/auth/session || exit 1
CMD ["node", "apps/admin-web/server.js"]

LABEL org.opencontainers.image.title="tcs-admin-web" \
      org.opencontainers.image.description="Trading Capture System — admin-web (Next.js BFF)" \
      org.opencontainers.image.source="https://github.com/davidyang07/trading-capture-system" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Trading Capture System"
