##
# Multi-stage build for Spring Boot backend
##

FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy Maven metadata only (for better layer caching)
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn -B dependency:go-offline -DskipTests=true || true


# Copy source code
COPY src ./src

# Build the application
RUN mvn -B clean package -DskipTests -Dmaven.test.skip=true -Dmaven.javadoc.skip=true

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

# Copy built jar (use wildcard to keep version-independent)
COPY --from=build /app/target/warehouse-management-*.jar app.jar

## IMPORTANT (Railway + Volumes)
## Railway volumes are typically mounted as root:root and only writable by root.
## In the previous configuration the app was running as a non-root user (appuser),
## so it could not create directories under the mounted volume and we were getting
## AccessDeniedException during photo uploads.
##
## To make this feature work reliably on Railway with volumes, we currently run
## the container as root. This is a pragmatic choice specific to this environment.

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run with optimized JVM flags for Railway
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
