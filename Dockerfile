FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy Maven metadata and download dependencies once
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the rest of the source and build the jar
COPY src ./src
RUN mvn -B clean package -DskipTests

# Production stage with minimal base image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install only curl for health checks (no wget needed)
RUN apk add --no-cache curl

# Copy the built JAR file from build stage
COPY --from=build /app/target/warehouse-management-*.jar app.jar

# Create non-root user for security
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001 -G appuser

USER appuser

# Expose port
EXPOSE 8080

# Optimized health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application with optimized JVM settings
ENTRYPOINT ["java", "-jar", "app.jar"]
