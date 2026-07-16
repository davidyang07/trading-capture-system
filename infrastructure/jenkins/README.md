# Jenkins

Populated in **Phase 13**: a declarative Jenkinsfile — checkout → secret scan → lint/format → backend unit tests → frontend lint/type/tests → integration tests (Testcontainers) → package → image builds → vulnerability scan → optional deploy. Tests are never skipped in CI. The reference Jenkinsfile is deliberately not migrated (it embeds a credential and private registries — see `docs/reference/security-findings.md`).
