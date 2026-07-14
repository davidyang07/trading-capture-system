# Trading Capture System Agent Instructions

## Project objective

Build a polished, self-contained, portfolio-ready distributed Trading Capture System using the strongest implementations found in the sibling `tcs-reference` directory.

The finished project should meaningfully demonstrate:

- Java and Spring Boot
- Spring MVC and REST APIs
- Spring Data JPA and Hibernate
- MySQL
- Kafka and versioned event contracts
- Redis
- OAuth 2.0 and OpenID Connect
- Spring Security
- React, Next.js, and TypeScript
- JUnit, Mockito, integration testing, and Testcontainers
- Docker and Docker Compose
- Kubernetes
- Jenkins
- observability and resilience patterns

Every technology must have a legitimate architectural responsibility. Do not add unused dependencies merely to list technologies.

## Repository boundaries

- The active Git repository is `trading-capture-system`.
- The sibling `tcs-reference` directory contains historical course repositories and is strictly read-only.
- Never edit, rename, move, delete, or format anything under `tcs-reference`.
- Never initialize Git inside `tcs-reference`.
- Never copy credentials, secrets, tokens, private keys, encrypted credentials, private artifact settings, or training-only service URLs from the reference repositories.
- Report discovered secrets only by file location and secret type. Never print secret values.
- Never attempt to add `tcs-reference` to this Git repository.

## Engineering rules

- Investigate existing implementations before introducing new abstractions.
- Select and document a clear source of truth when duplicate implementations exist.
- Distinguish code that should be migrated, refactored, replaced, or excluded.
- Address root causes rather than suppressing errors.
- Never weaken validation or disable tests merely to obtain a passing build.
- Do not leave fake production responses, empty production methods, placeholder endpoints, or unfinished production TODOs.
- Do not silently ignore exceptions.
- Do not depend on course-hosted infrastructure or inaccessible private Maven artifacts.
- Do not expose persistence entities directly as public API contracts without a documented reason.
- Use `BigDecimal` for financial amounts, prices, rates, and quantities.
- Use explicit UTC-aware timestamp handling.
- Preserve immutable audit history for execution corrections and cancellations.
- Keep service ownership and database ownership boundaries explicit.
- Record material architecture decisions in ADRs.
- Keep documentation synchronized with application behavior and contracts.

## Git and safety rules

- Do not push to any Git remote.
- Do not force-push.
- Do not rewrite Git history.
- Do not run destructive resets.
- Do not clean untracked files without explicit user approval.
- Do not delete large directories without explicit user approval.
- Do not commit active `.env` files or secrets.
- Do not modify files outside the active repository.
- Local checkpoint commits may be created only after verification succeeds.

## Initial workflow

- The first Claude Code session is investigation and planning only.
- Claude must present the proposed architecture and implementation plan before changing application code.
- Ask only questions that materially affect architecture or block progress.
- For non-blocking uncertainty, choose a sensible enterprise-grade default and document it.
- Implement the approved project in bounded and verifiable phases.
- After creating the complete Claude Code configuration, stop and request that Claude Code be restarted.

## Completion evidence

Never claim a phase is complete without reporting:

1. files changed,
2. functionality implemented,
3. architecture decisions,
4. commands run,
5. tests passed,
6. tests not run and why,
7. known limitations,
8. documentation updated,
9. recommended next phase.

Never claim an unexecuted command passed.
