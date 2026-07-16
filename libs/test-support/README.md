# test-support

Shared test infrastructure consumed with `<scope>test</scope>`: singleton reusable Testcontainers base classes (MySQL from Phase 3, Kafka from Phase 6/7, Redis from Phase 5) and JWT/security test fixtures (from Phase 3). Keeping containers singleton-per-JVM keeps integration suites fast and non-flaky. Deliberately empty until Phase 3 — no speculative helpers.
