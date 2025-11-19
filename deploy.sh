#!/bin/bash

# Warehouse Management System Deployment Script
# Usage: ./deploy.sh [environment]

set -e

ENVIRONMENT=${1:-production}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🚀 Deploying Warehouse Management System to $ENVIRONMENT environment..."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

USE_COMPOSE_V2=false
if docker compose version &> /dev/null; then
    USE_COMPOSE_V2=true
fi

compose() {
    if [ "$USE_COMPOSE_V2" = true ]; then
        docker compose "$@"
    else
        docker-compose "$@"
    fi
}

FLYWAY_DATABASE=${FLYWAY_DATABASE:-warehouse_db}
FLYWAY_URL=${FLYWAY_URL:-jdbc:postgresql://localhost:5432/${FLYWAY_DATABASE}}
FLYWAY_USER=${FLYWAY_USER:-warehouse_user}
FLYWAY_PASSWORD=${FLYWAY_PASSWORD:-warehouse_pass}
SKIP_FLYWAY=${SKIP_FLYWAY:-false}

run_flyway_migrations() {
    if [ "$SKIP_FLYWAY" = true ]; then
        echo "⚠️  Skipping Flyway migrations (SKIP_FLYWAY=true)"
        return
    fi

    echo "🐘 Starting PostgreSQL container for migrations..."
    compose up -d db

    echo "⏳ Waiting for PostgreSQL to accept connections..."
    DB_CONTAINER=""
    until [ -n "$DB_CONTAINER" ]; do
        DB_CONTAINER=$(compose ps -q db)
        sleep 2
    done

    until docker exec "$DB_CONTAINER" pg_isready -U "$FLYWAY_USER" -d "$FLYWAY_DATABASE" > /dev/null 2>&1; do
        sleep 3
    done

    echo "🧬 Applying Flyway migrations to $FLYWAY_URL ..."
    mvn -DskipTests -Dflyway.url="$FLYWAY_URL" -Dflyway.user="$FLYWAY_USER" -Dflyway.password="$FLYWAY_PASSWORD" flyway:migrate
}

# Stop existing containers
echo "🛑 Stopping existing containers..."
compose down || true

# Remove old images (optional)
echo "🧹 Cleaning up old images..."
docker image prune -f || true

# Run Flyway migrations against the target database
run_flyway_migrations

# Build and start services
echo "🔨 Building and starting services..."
echo "📦 Building optimized Maven project..."
mvn clean package -DskipTests -Dmaven.test.skip=true -Dmaven.javadoc.skip=true

echo "🐳 Building optimized Docker images..."
echo "   - Multi-stage build with OpenJDK 17 JRE Alpine"
echo "   - Optimized for production deployment"
echo "   - Minimal base image for smaller size"

compose build --parallel
compose up -d

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check if services are running
echo "🔍 Checking service status..."
compose ps

# Run health checks
echo "🏥 Running health checks..."

# Backend health check
if curl -f http://localhost:8080/actuator/health &> /dev/null; then
    echo "✅ Backend is healthy"
else
    echo "❌ Backend health check failed"
    exit 1
fi

# Frontend health check
if curl -f http://localhost/health &> /dev/null; then
    echo "✅ Frontend is healthy"
else
    echo "❌ Frontend health check failed"
    exit 1
fi

echo ""
echo "🎉 Deployment completed successfully!"
echo ""
echo "📊 Service URLs:"
echo "   Frontend: http://localhost"
echo "   Backend API: http://localhost/api"
echo "   PostgreSQL: localhost:5432"
echo "   pgAdmin: http://localhost:5050"
echo "   H2 Console: http://localhost:8080/h2-console (if using H2)"
echo ""
echo "🔐 Database Credentials:"
echo ""
echo "PostgreSQL (Production):"
echo "   Host: localhost:5432"
echo "   Database: warehouse_db"
echo "   Username: warehouse_user"
echo "   Password: warehouse_pass"
echo ""
echo "pgAdmin (Management Tool):"
echo "   URL: http://localhost:5050"
echo "   Email: admin@warehouse.com"
echo "   Password: admin123"
echo ""
echo "H2 (Development - if using):"
echo "   URL: jdbc:h2:mem:warehouse_db"
echo "   Username: sa"
echo "   Password: password"
echo ""
echo "📝 To view logs:"
if [ "$USE_COMPOSE_V2" = true ]; then
    echo "   Backend: docker compose logs backend"
    echo "   Frontend: docker compose logs frontend"
    echo "   pgAdmin: docker compose logs pgadmin"
else
    echo "   Backend: docker-compose logs backend"
    echo "   Frontend: docker-compose logs frontend"
    echo "   pgAdmin: docker-compose logs pgadmin"
fi
echo ""
echo "🚀 For Railway deployment (recommended):"
echo "   1. Visit: https://railway.app"
echo "   2. Sign up with GitHub"
echo "   3. Install CLI: npm install -g @railway/cli"
echo "   4. Login: railway login"
echo "   5. Deploy: railway init && railway add postgresql && railway up"
echo ""
echo "🛑 To stop services:"
if [ "$USE_COMPOSE_V2" = true ]; then
    echo "   docker compose down"
else
    echo "   docker-compose down"
fi
echo ""
