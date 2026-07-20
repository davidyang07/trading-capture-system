# Trading Capture System — trading-service image (Phase 12: functional full-stack image).
# Multi-stage, pinned bases, non-root runtime, HEALTHCHECK. Phase 13 performs final
# hardening (image slimming, trivy scanning) and adds the K8s/Jenkins delivery path.
#
# Build context is the repository root (the reactor needs the parent POM, .mvn settings,
# and the shared libs), e.g.:
#   docker build -f services/trading-service/Dockerfile -t tcs/trading-service .

# --- build stage: package the fat jar with the project-isolated Maven wrapper ------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy the whole reactor. .dockerignore keeps target/, node_modules/, and local docs out.
COPY . .
# -pl the module, -am to also build its shared libs. Tests run in the Maven gate, not here.
RUN sh ./mvnw -B -ntp -DskipTests -pl services/trading-service -am package

# --- runtime stage: JRE only, non-root -------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
# Non-root runtime user (no shell, no home writes needed).
RUN groupadd --system tcs && useradd --system --gid tcs --home /app --shell /usr/sbin/nologin tcs
WORKDIR /app
COPY --from=build /workspace/services/trading-service/target/*.jar /app/app.jar
USER tcs
EXPOSE 8081
# JSON structured logs in containers (Phase 11 json-logs profile); override via env.
ENV SPRING_PROFILES_ACTIVE=json-logs \
    SERVICE_NAME=trading-service \
    JAVA_OPTS=""
# Readiness probe uses the actuator readiness group (management.endpoint.health.probes.enabled).
# The temurin jammy image ships bash; /dev/tcp keeps the JRE layer dependency-free (same
# technique the compose infra healthchecks use for images without curl/wget).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && echo -e 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q 'UP' <&3"]
ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
