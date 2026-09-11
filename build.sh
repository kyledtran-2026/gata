#!/bin/bash
set -e  # Exit on error

BUILD_NUMBER=${BUILD:-latest}

echo "Building Maven project with revision $BUILD_NUMBER..."
mvn $1 clean package -Drevision=$BUILD_NUMBER -DskipTests  # Add -DskipTests if you want to speed it up

# Determine the JAR file name (Spring Boot executable JAR)
JAR_FILE=$(ls target/*.jar | grep -v "sources.jar" | grep -v "javadoc.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "Error: No JAR file found in target/ directory!"
  exit 1
fi

echo "Found JAR: $JAR_FILE"

# Build the Docker image
echo "Building Docker image..."
docker build \
  --build-arg JAR_FILE="$JAR_FILE" \
  -t kdt/gata:$BUILD_NUMBER \
  -f Dockerfile .   # or just . if Dockerfile is in root

echo "Build successful! Image tagged as kdt/gata:$BUILD_NUMBER"