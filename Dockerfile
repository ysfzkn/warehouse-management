##
# Multi-stage build for Spring Boot backend
##

FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy Maven metadata and warm dependency cache (improves Docker layer reuse)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source after dependencies to avoid invalidating cache on every change
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

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
##
## If you want a stricter, best-practice setup (non-root runtime), you can:
## - Mount the volume as usual (root:root)
## - Use an entrypoint script to chown the volume paths at startup
## - Then drop privileges to a non-root user (e.g. appuser) before starting the app.
##
## Example of the old non-root setup (kept here for reference):
##
## RUN addgroup -g 1001 -S appuser && \
##     adduser -S appuser -u 1001 -G appuser
##
## USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
