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

RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001 -G appuser

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
