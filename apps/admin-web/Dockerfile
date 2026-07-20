# Trading Capture System — admin-web image (Phase 12: functional full-stack image).
# Multi-stage Next.js standalone build, pnpm via corepack, non-root runtime, HEALTHCHECK.
# Phase 13 performs final hardening (image slimming, trivy scan) + the delivery path.
#
# Build context is the repository root (pnpm workspace: lockfile + workspace manifest live
# there); admin-web has no workspace-internal dependencies. Example:
#   docker build -f apps/admin-web/Dockerfile -t tcs/admin-web .
#
# Standalone output is enabled here (NEXT_OUTPUT_STANDALONE=true); the Linux build layer can
# create the file-trace symlinks that Windows without Developer Mode cannot (see next.config.js).

FROM node:22-alpine AS base
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
FROM base AS runtime
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
WORKDIR /app
# The monorepo standalone output mirrors the repo layout under apps/admin-web/.
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/.next/static ./apps/admin-web/.next/static
COPY --from=build --chown=nextjs:nodejs /repo/apps/admin-web/public ./apps/admin-web/public
USER nextjs
EXPOSE 3000
# Auth.js exposes /api/auth/session as an unauthenticated 200 — a dependency-free readiness probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD wget -q -O /dev/null http://localhost:3000/api/auth/session || exit 1
CMD ["node", "apps/admin-web/server.js"]
