#!/bin/bash
# Railway build script with Docker Hub authentication

set -e

echo "🔐 Authenticating with Docker Hub..."

# Check if Docker Hub credentials are provided
if [ -n "$DOCKER_USERNAME" ] && [ -n "$DOCKER_PASSWORD" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
  echo "✅ Docker Hub authentication successful"
else
  echo "⚠️  Docker Hub credentials not found. Using unauthenticated pulls (may hit rate limits)"
fi

echo "🔨 Building Docker image..."
docker build -t railway-app .

echo "✅ Build completed successfully"

