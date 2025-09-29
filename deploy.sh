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

# Stop existing containers
echo "🛑 Stopping existing containers..."
docker-compose down || true

# Remove old images (optional)
echo "🧹 Cleaning up old images..."
docker image prune -f || true

# Build and start services
echo "🔨 Building and starting services..."
echo "📦 Building Maven project first..."
mvn clean package -DskipTests

if docker compose version &> /dev/null; then
    docker compose build --no-cache
    docker compose up -d
else
    docker-compose build --no-cache
    docker-compose up -d
fi

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check if services are running
echo "🔍 Checking service status..."
if docker compose version &> /dev/null; then
    docker compose ps
else
    docker-compose ps
fi

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
echo "   Backend: docker-compose logs backend"
echo "   Frontend: docker-compose logs frontend"
echo "   pgAdmin: docker-compose logs pgadmin"
echo ""
echo "🚀 For Railway deployment (recommended):"
echo "   1. Visit: https://railway.app"
echo "   2. Sign up with GitHub"
echo "   3. Install CLI: npm install -g @railway/cli"
echo "   4. Login: railway login"
echo "   5. Deploy: railway init && railway add postgresql && railway up"
echo ""
echo "🛑 To stop services:"
echo "   docker-compose down"
echo ""
