# test-support

Shared test infrastructure consumed with `<scope>test</scope>`: singleton reusable MySQL, Kafka,
Redis, and Keycloak Testcontainers plus JWT/security fixtures. Keeping containers singleton-per-JVM
keeps integration suites fast and non-flaky while exercising real infrastructure and real issued
tokens.
