# syntax=docker/dockerfile:1

# =====================================================================
# praxedo-upload-backend - container image
# Multi-stage: build the Spring Boot fat jar with Maven + JDK 21,
# run it on a slim JRE 21. Tests are skipped here (they run in CI).
# The SAME image is used for both Cloud Run services (api + worker);
# behaviour is selected at runtime by SPRING_PROFILES_ACTIVE=gcp.
# =====================================================================

# ---- Stage 1: build ----
# No Maven wrapper is committed, so we build from the official Maven image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached unless pom.xml changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Build the application. Tests are executed in CI, not in the image build.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system app \
 && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

# The Spring Boot plugin repackages the main artifact as the runnable fat jar
# (the "*.jar.original" left behind does not match "*.jar").
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

# Cloud Run routes traffic to this port (PORT/SERVER_PORT default 8080).
EXPOSE 8080

# JAVA_OPTS is overridable; MaxRAMPercentage keeps the JVM inside the
# container memory limit set by Cloud Run.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -jar /app/app.jar"]
