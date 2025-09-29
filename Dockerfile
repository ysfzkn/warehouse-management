# Optimized multi-stage build for Spring Boot application
FROM openjdk:17-jdk-alpine AS build

WORKDIR /app

# Install only necessary packages
RUN apk add --no-cache maven

# Copy Maven files first for better layer caching
COPY pom.xml ./
COPY src ./src/

# Build the application with optimizations
RUN mvn clean package -DskipTests -Dmaven.repo.local=/tmp/maven-repo

# Production stage with minimal base image
FROM openjdk:17-alpine

WORKDIR /app

# Install only curl for health checks (no wget needed)
RUN apk add --no-cache curl

# Copy the built JAR file from build stage
COPY --from=build /app/target/warehouse-management-1.0.0.jar app.jar

# Create non-root user for security
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001 -G appuser

USER appuser

# Expose port
EXPOSE 8080

# Optimized health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f --max-time 5 http://localhost:8080/actuator/health || exit 1

# Run the application with optimized JVM settings
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
