#!/usr/bin/env bash
set -euo pipefail

# Required env vars
: "${PROJECT_ID:?Set PROJECT_ID}"
: "${NEON_USERNAME_VALUE:?Set NEON_USERNAME_VALUE}"
: "${NEON_PASSWORD_VALUE:?Set NEON_PASSWORD_VALUE}"

gcloud config set project "${PROJECT_ID}"

echo -n "${NEON_USERNAME_VALUE}" | gcloud secrets create NEON_USERNAME --data-file=- || \
  echo -n "${NEON_USERNAME_VALUE}" | gcloud secrets versions add NEON_USERNAME --data-file=-

echo -n "${NEON_PASSWORD_VALUE}" | gcloud secrets create NEON_PASSWORD --data-file=- || \
  echo -n "${NEON_PASSWORD_VALUE}" | gcloud secrets versions add NEON_PASSWORD --data-file=-
