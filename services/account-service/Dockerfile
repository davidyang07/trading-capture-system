# Trading Capture System — account-service image (Phase 12: functional full-stack image).
# Multi-stage, pinned bases, non-root runtime, HEALTHCHECK. Phase 13 does final hardening.
# Build context is the repository root:
#   docker build -f services/account-service/Dockerfile -t tcs/account-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN sh ./mvnw -B -ntp -DskipTests -pl services/account-service -am package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system tcs && useradd --system --gid tcs --home /app --shell /usr/sbin/nologin tcs
WORKDIR /app
COPY --from=build /workspace/services/account-service/target/*.jar /app/app.jar
USER tcs
EXPOSE 8082
ENV SPRING_PROFILES_ACTIVE=json-logs \
    SERVICE_NAME=account-service \
    JAVA_OPTS=""
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8082 && echo -e 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q 'UP' <&3"]
ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
