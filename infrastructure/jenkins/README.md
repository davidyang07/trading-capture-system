# Jenkins

Populated in **Phase 13**: a declarative Jenkinsfile — checkout → secret scan → lint/format → backend unit tests → frontend lint/type/tests → integration tests (Testcontainers) → package → image builds → vulnerability scan → optional deploy. Tests are never skipped in CI. The pipeline is authored fresh, with no embedded credentials or private registries.
