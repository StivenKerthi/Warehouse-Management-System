# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Dependency cache
#   Downloading dependencies is the slowest step. By copying pom.xml first and
#   running go-offline before the source copy, Docker caches this layer and only
#   re-runs it when pom.xml changes — not on every source edit.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS deps

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Build
#   Full source compile + package. Tests are skipped here; they run in CI or
#   via `mvn test` locally against real infrastructure.
# ─────────────────────────────────────────────────────────────────────────────
FROM deps AS build

COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Runtime
#   Minimal JRE image. No Maven, no JDK, no source code in the final layer.
#   Runs as a non-root user for security best practices.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a dedicated system user — never run as root in production
RUN addgroup -S wms && adduser -S wms -G wms

WORKDIR /app

# Copy only the executable jar from the build stage
COPY --from=build /build/target/*.jar app.jar

# Switch to non-root
USER wms

EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport      — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0     — use up to 75% of container RAM for heap
#   -Djava.security.egd=...       — faster SecureRandom (avoids /dev/random blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
