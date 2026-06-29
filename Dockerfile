##
# Optimized Spring Boot build for Railway
##

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy pom.xml and resolve dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:resolve dependency:resolve-plugins --fail-never

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests --batch-mode

##
# Runtime stage
##
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# curl: Railway healthcheck. libwebp-tools: provides `dwebp`, used as a fallback to
# decode WebP images that the pure-Java reader can't (some crawled CDN WebPs).
RUN apk add --no-cache curl libwebp-tools

# Copy the built jar
COPY --from=build /app/target/warehouse-management-*.jar app.jar

# Railway runs as root for volume access
EXPOSE 8080

# Healthcheck for Railway
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM settings optimized for Railway containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT sh -c "java $JAVA_OPTS -jar app.jar"
