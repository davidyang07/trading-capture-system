# ADR-0011: Monorepo build = root Maven aggregator + pnpm workspace

Status: Accepted (2026-07-14)

## Context
Four Spring Boot services and three Java libraries must build consistently with one version story; the frontend needs its own Node toolchain — unified here rather than as disconnected single-module repos.

## Decision
A root aggregator `pom.xml` (packaging `pom`, parent `spring-boot-starter-parent`) lists `libs/*` and `services/*` as modules — one Boot/Java version everywhere, `./mvnw -pl <module>` for targeted builds, Maven Wrapper committed. `apps/admin-web` lives in a pnpm workspace (root `package.json` + `pnpm-workspace.yaml`, `packageManager` pinned) and never enters the Maven build.

## Consequences
- Single-command whole-repo verification (`./mvnw verify`, `pnpm -r build`) and per-module fast loops.
- Version bumps happen in exactly one place per ecosystem.
- Rejected: Gradle (references and reviewer familiarity are Maven), Nx/Turborepo (frontend is one app), per-service parents (version drift).
