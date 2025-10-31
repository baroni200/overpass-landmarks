# Multi-stage Dockerfile for Railway deployment
# Stage 1: Build stage
FROM eclipse-temurin:22-jdk-jammy AS build

WORKDIR /app

# Copy Gradle wrapper and build files first (for better caching)
COPY gradle/ gradle/
COPY gradlew build.gradle.kts ./
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the application (skip tests - they run in CI/CD)
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime stage
FROM eclipse-temurin:22-jre-jammy

# Add metadata labels
LABEL maintainer="overpass-landmarks"
LABEL description="Overpass Landmarks Service - Spring Boot application"
LABEL version="1.0.0"

# Install curl for health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Set working directory
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port (Railway will override PORT env var)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run the application with optimized JVM settings
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]
