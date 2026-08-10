# RepoLens — multi-stage OCI image (Docker/Podman compatible)
# Build:  podman build -t repolens:local .
# Run:    podman run --rm -p 8080:8080 repolens:local

# ---- build ----
FROM eclipse-temurin:21-jdk-jammy AS build

# Prefer IPv4; some container/VM networks break Java HTTPS redirect handling otherwise
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Gradle wrapper + project metadata first (better layer caching)
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Pre-seed the Gradle wrapper distribution (curl is more reliable than the
# wrapper's Java downloader across local Podman VM networks). Hash directory
# matches distributionUrl in gradle/wrapper/gradle-wrapper.properties.
RUN mkdir -p /root/.gradle/wrapper/dists/gradle-8.12.1-bin/eumc4uhoysa37zql93vfjkxy0 \
    && curl -fsSL --retry 5 --retry-delay 2 \
        -o /root/.gradle/wrapper/dists/gradle-8.12.1-bin/eumc4uhoysa37zql93vfjkxy0/gradle-8.12.1-bin.zip \
        https://services.gradle.org/distributions/gradle-8.12.1-bin.zip \
    && touch /root/.gradle/wrapper/dists/gradle-8.12.1-bin/eumc4uhoysa37zql93vfjkxy0/gradle-8.12.1-bin.zip.lck \
    && touch /root/.gradle/wrapper/dists/gradle-8.12.1-bin/eumc4uhoysa37zql93vfjkxy0/gradle-8.12.1-bin.zip.ok

# Module build scripts and sources (existing modular monolith)
COPY repolens-core ./repolens-core
COPY repolens-ingest ./repolens-ingest
COPY repolens-parse ./repolens-parse
COPY repolens-analyzers ./repolens-analyzers
COPY repolens-api-model ./repolens-api-model
COPY repolens-cli ./repolens-cli
COPY repolens-web ./repolens-web

RUN chmod +x gradlew \
    && ./gradlew --no-daemon :repolens-web:installDist

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime

# git is required for existing public GitHub HTTPS shallow-clone ingestion
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Non-root user (no architectural change; filesystem ownership only)
RUN groupadd --system repolens \
    && useradd --system --gid repolens --home-dir /app --shell /usr/sbin/nologin repolens

WORKDIR /app

COPY --from=build /workspace/repolens-web/build/install/repolens-web /app

RUN chown -R repolens:repolens /app

USER repolens

EXPOSE 8080

# Existing application already binds 0.0.0.0 and reads PORT (defaults to 8080)
CMD ["./bin/repolens-web"]
