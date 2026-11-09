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

# Run as an unprivileged user. A remote-code-execution bug in a container running as
# root gives the attacker root inside it — including write access to every mounted
# volume. The uploads/data directory is chowned so the app can still write to it.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data/uploads \
    && chown -R app:app /app /data
USER app

EXPOSE 8080

# Healthcheck for Railway
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM memory settings. Railway bills the memory the container actually holds, and the
# container "sees" the whole plan limit (8 GB+), so a percentage-based cap let the heap
# grow to ~6 GB and G1 never handed freed memory back — RAM sat flat at ~1.6 GB.
# - Xmx1024m: a hard cap that still leaves room for the worst case we handle — a 20 MB
#   upload decoded to a full-size BufferedImage (a 48 MP photo is ~190 MB per copy, and
#   resizing holds two), with a couple of those in flight at once.
# - G1PeriodicGCInterval + the free ratios: when the app is idle, run a GC every minute
#   and return unused heap to the OS, so the bill follows real load instead of the peak.
# Override without a code change by setting JAVA_OPTS on the Railway service (a variable
# there replaces this whole line), e.g. to raise -Xmx if an OutOfMemoryError ever shows up.
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xms256m -Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT sh -c "java $JAVA_OPTS -jar app.jar"
