#!/usr/bin/env bash
set -euo pipefail

# Required env vars
: "${PROJECT_ID:?Set PROJECT_ID}"
: "${REGION:?Set REGION e.g. us-central1}"
: "${SERVICE_NAME:?Set SERVICE_NAME e.g. pgms-backend}"
: "${NEON_HOST:?Set NEON_HOST}"
: "${NEON_DATABASE_NAME:?Set NEON_DATABASE_NAME}"

IMAGE="gcr.io/${PROJECT_ID}/${SERVICE_NAME}"

# Build JAR
mvn -DskipTests clean package

# Build and push container with Cloud Build (no local Docker daemon required)
gcloud builds submit --tag "${IMAGE}" .

# Deploy Cloud Run
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE}" \
  --platform managed \
  --region "${REGION}" \
  --allow-unauthenticated \
  --set-env-vars "SPRING_DATASOURCE_URL=jdbc:postgresql://${NEON_HOST}/${NEON_DATABASE_NAME}?sslmode=require" \
  --set-env-vars "DDL_AUTO=update,HIBERNATE_FORMAT_SQL=false" \
  --set-secrets "NEON_USERNAME=NEON_USERNAME:latest,NEON_PASSWORD=NEON_PASSWORD:latest" \
  --set-env-vars "app.cors.allowed-origins=http://localhost:5173"
