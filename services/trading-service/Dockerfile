# Trading Capture System — hardened trading-service delivery image.
# Multi-stage, DIGEST-PINNED bases (supply-chain reproducibility), non-root numeric UID
# (K8s runAsNonRoot), OCI labels, container-aware JVM, HEALTHCHECK. Scanned with trivy in CI
# (infrastructure/jenkins/Jenkinsfile) against infrastructure/trivy/trivy.yaml.
#
# Build context is the repository root (the reactor needs the parent POM, .mvn settings, and
# the shared libs). The per-Dockerfile services/trading-service/Dockerfile.dockerignore keeps
# apps/, docs/, and build output out of the context:
#   docker build -f services/trading-service/Dockerfile -t tcs/trading-service:local .

# --- build stage: package the fat jar with the project-isolated Maven wrapper ------------
# maven:3.9-eclipse-temurin-21 (pinned to the multi-arch manifest digest for reproducibility).
FROM maven:3.9-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build
WORKDIR /workspace
# Copy the whole reactor. Dockerfile.dockerignore keeps target/, apps/, docs/ out.
COPY . .
# -pl the module, -am to also build its shared libs. Tests run in the Maven/CI gate, not here.
RUN sh ./mvnw -B -ntp -DskipTests -pl services/trading-service -am package \
 && cp services/trading-service/target/*.jar /workspace/app.jar

# --- runtime stage: JRE only, non-root -------------------------------------------------
# eclipse-temurin:21-jre-jammy (pinned to the multi-arch manifest digest).
FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13 AS runtime
# Non-root runtime user with an explicit high numeric UID/GID so the K8s pod securityContext
# can assert runAsNonRoot/runAsUser: 10001 (a purely-named user cannot satisfy runAsNonRoot).
RUN groupadd --system --gid 10001 tcs \
 && useradd --system --uid 10001 --gid tcs --home /app --shell /usr/sbin/nologin tcs
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
# JSON structured logs in containers (`json-logs` profile); container-aware heap sizing
# so the JVM honours the pod memory limit. Override JAVA_OPTS via env.
ENV SPRING_PROFILES_ACTIVE=json-logs \
    SERVICE_NAME=trading-service \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
# Readiness probe uses the actuator readiness group (management.endpoint.health.probes.enabled).
# The temurin jammy image ships bash; /dev/tcp keeps the JRE layer dependency-free (same
# technique the compose infra healthchecks use for images without curl/wget).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${MANAGEMENT_SERVER_PORT:-8081} && echo -e 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q 'UP' <&3"]
# exec form so java replaces the shell as PID 1 and receives SIGTERM (Spring graceful shutdown).
ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

LABEL org.opencontainers.image.title="tcs-trading-service" \
      org.opencontainers.image.description="Trading Capture System — trading-service" \
      org.opencontainers.image.source="https://github.com/davidyang07/trading-capture-system" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Trading Capture System"
