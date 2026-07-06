# syntax=docker/dockerfile:1

# --- Stage 1: build the React SPA (produces frontend/dist with hashed assets) ---
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot jar (bundles the SPA from stage 1) ---
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
# Warm the dependency cache before copying source so code changes don't refetch
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
# The Vite output must be present so the copy-frontend plugin folds it into the jar
COPY --from=frontend /app/frontend/dist/ frontend/dist/
RUN ./mvnw -q -B -DskipTests package

# --- Stage 3: minimal runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a non-root user
RUN useradd --system --uid 1001 omivertex
COPY --from=backend /app/target/omivertex-*.jar app.jar
USER omivertex
EXPOSE 8080
# prod profile: no default secrets, Secure cookie, Flyway, ddl-auto=validate.
# All secrets come from the environment (see docs/DEPLOYMENT.md).
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
