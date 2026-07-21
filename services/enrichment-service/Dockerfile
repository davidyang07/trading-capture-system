# Trading Capture System — hardened enrichment-service delivery image.
# Multi-stage, DIGEST-PINNED bases, non-root numeric UID (K8s runAsNonRoot), OCI labels,
# container-aware JVM, HEALTHCHECK. Scanned with trivy in CI. Build context is the repo root:
#   docker build -f services/enrichment-service/Dockerfile -t tcs/enrichment-service:local .

# maven:3.9-eclipse-temurin-21 (pinned to the multi-arch manifest digest).
FROM maven:3.9-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build
WORKDIR /workspace
COPY . .
RUN sh ./mvnw -B -ntp -DskipTests -pl services/enrichment-service -am package \
 && cp services/enrichment-service/target/*.jar /workspace/app.jar

# eclipse-temurin:21-jre-jammy (pinned to the multi-arch manifest digest).
FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13 AS runtime
RUN groupadd --system --gid 10001 tcs \
 && useradd --system --uid 10001 --gid tcs --home /app --shell /usr/sbin/nologin tcs
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8083
ENV SPRING_PROFILES_ACTIVE=json-logs \
    SERVICE_NAME=enrichment-service \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8083 && echo -e 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q 'UP' <&3"]
ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

LABEL org.opencontainers.image.title="tcs-enrichment-service" \
      org.opencontainers.image.description="Trading Capture System — enrichment-service" \
      org.opencontainers.image.source="https://github.com/davidyang07/trading-capture-system" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Trading Capture System"
