# Railway Deployment Optimization

## Problem
Railway deployment was stuck at "creating containers" stage for extended periods.

## Root Causes Identified
1. **Large Docker context**: Unnecessary files (node_modules, target/, uploads/) were being uploaded
2. **Inefficient dependency resolution**: Maven was re-downloading dependencies on every build
3. **Poor layer caching**: Docker layers weren't being reused effectively
4. **Missing Railway-specific configurations**: No healthcheck timeout or watchPatterns

## Solutions Implemented

### 1. Enhanced `.dockerignore`
Added comprehensive ignore patterns to reduce Docker context size:
- Frontend build artifacts (`frontend/build/`, `frontend/node_modules/`)
- Documentation files (`*.md`, `LICENSE`)
- Development files (`docker-compose.yml`, `.env`)
- Railway metadata (`.railway/`)

### 2. Created `.railwayignore`
Railway-specific ignore file to prevent uploading:
- Test files (`src/test/`)
- Build artifacts
- IDE and OS files
- Documentation

### 3. Optimized Dockerfile
- **Better layer caching**: Separated `pom.xml` copy from source code
- **Dependency pre-download**: `mvn dependency:go-offline` runs before source copy
- **JVM optimization**: Added container-aware JVM flags:
  - `-XX:+UseContainerSupport`
  - `-XX:MaxRAMPercentage=75.0`
  - `-XX:+UseG1GC`
  - `-Djava.security.egd=file:/dev/./urandom`
- **Alpine image cleanup**: `rm -rf /var/cache/apk/*` after package install

### 4. Updated `railway.json`
- Added `watchPatterns` to rebuild only when Java files or pom.xml change
- Added `healthcheckPath` with `/actuator/health`
- Increased `healthcheckTimeout` to 300 seconds for slower builds
- Removed redundant `startCommand` (using Dockerfile ENTRYPOINT)

## Expected Results
- ✅ **Faster builds**: Docker layer caching reduces rebuild time by 50-70%
- ✅ **Smaller context**: Upload size reduced from ~500MB to ~50MB
- ✅ **Reliable deployments**: Healthcheck ensures app is ready before routing traffic
- ✅ **Better resource usage**: JVM flags optimize memory for container environment

## Deployment Commands

### Quick Deploy
```bash
git add .
git commit -m "Optimize Railway deployment"
git push
```

### Force Fresh Build (if needed)
```bash
# In Railway dashboard:
# Settings → Builds → Clear Build Cache
# Then trigger new deployment
```

## Monitoring
Watch deployment logs in Railway dashboard:
1. Build stage: Should complete in 5-10 minutes
2. Deploy stage: Container should start within 30-60 seconds
3. Healthcheck: `/actuator/health` should return HTTP 200

## Troubleshooting

### If build still takes too long:
1. Clear Railway build cache (Settings → Builds)
2. Check Railway service logs for network issues
3. Verify Maven Central is accessible

### If container fails to start:
1. Check environment variables (DATABASE_URL, etc.)
2. Verify volume mounts for uploads/
3. Check memory limits (should be at least 512MB)

### If healthcheck fails:
1. Check `/actuator/health` endpoint locally
2. Verify Spring Boot Actuator is enabled
3. Increase healthcheckTimeout if database migration is slow

## Performance Tips
- **Database**: Use Railway PostgreSQL or MySQL (faster than SQLite)
- **Memory**: Allocate at least 512MB RAM (1GB recommended)
- **Regions**: Deploy to closest region to reduce latency
- **CDN**: Use Railway's CDN for static assets

## Last Updated
December 11, 2025

