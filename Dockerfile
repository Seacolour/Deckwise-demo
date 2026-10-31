FROM eclipse-temurin:25-jre

ARG PANDOC_VERSION=3.9.0.2
ARG TARGETARCH
ARG PANDOC_RELEASE_BASE_URL=https://github.com/jgm/pandoc/releases/download
ARG PANDOC_SHA256=

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-Dserver.address=0.0.0.0" \
    PANDOC_PATH="/usr/local/bin/pandoc" \
    PANDOC_EXECUTABLE_PATH="/usr/local/bin/pandoc"

WORKDIR /app

# Install Pandoc in the runtime image so it no longer needs the former private base image.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates tar; \
    case "${TARGETARCH:-amd64}" in \
      amd64) PANDOC_ARCH=amd64 ;; \
      arm64) PANDOC_ARCH=arm64 ;; \
      *) echo "Unsupported TARGETARCH: ${TARGETARCH:-unset}" >&2; exit 1 ;; \
    esac; \
    PANDOC_ARCHIVE="pandoc-${PANDOC_VERSION}-linux-${PANDOC_ARCH}.tar.gz"; \
    curl -fL --retry 5 --retry-delay 3 --retry-all-errors \
      "${PANDOC_RELEASE_BASE_URL}/${PANDOC_VERSION}/${PANDOC_ARCHIVE}" \
      -o /tmp/pandoc.tar.gz; \
    if [ -n "${PANDOC_SHA256}" ]; then \
      echo "${PANDOC_SHA256}  /tmp/pandoc.tar.gz" | sha256sum -c -; \
    fi; \
    tar -xzf /tmp/pandoc.tar.gz -C /tmp; \
    cp "/tmp/pandoc-${PANDOC_VERSION}/bin/pandoc" /usr/local/bin/pandoc; \
    mkdir -p /usr/local/share; \
    cp -r "/tmp/pandoc-${PANDOC_VERSION}/share/." /usr/local/share/; \
    chmod +x /usr/local/bin/pandoc; \
    pandoc --version; \
    rm -rf /tmp/pandoc.tar.gz "/tmp/pandoc-${PANDOC_VERSION}" /var/lib/apt/lists/*; \
    useradd -r -u 10001 -g root appuser

COPY target/*.jar /app/app.jar

EXPOSE 8104 40052

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
  CMD curl -fs http://localhost:8104/api/actuator/health || exit 1

USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
