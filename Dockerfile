## Build stage
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy project
COPY . .

# Ensure mvnw is executable (Linux containers)
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -q

# Pick the built jar (Spring Boot repackage)
RUN ls -1 target/*.jar | head -n 1 | xargs -I {} cp {} /workspace/app.jar

## Runtime stage
FROM eclipse-temurin:25-jre

WORKDIR /app

# Create non-root user
RUN useradd -r -u 10001 appuser

# Install grpc-health-probe for Docker HEALTHCHECK
ARG GRPC_HEALTH_PROBE_VERSION=v0.4.28
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates \
    && wget -qO /usr/local/bin/grpc_health_probe \
       "https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64" \
    && chmod +x /usr/local/bin/grpc_health_probe \
    && apt-get purge -y wget && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 41051

USER appuser

# Common JVM defaults for containers
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD ["grpc_health_probe", "-addr=:41051"]

ENTRYPOINT ["java","-jar","/app/app.jar"]

