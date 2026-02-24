# GCP Backend Hosting (Cloud Run)

## What is already done on this machine
- Google Cloud CLI installed (`gcloud`).
- Docker CLI installed (`docker`, `docker compose`, `buildx`).
- Backend Docker files created:
  - `Dockerfile`
  - `.dockerignore`
- Backend JAR builds successfully (`target/pg-management-backend-1.0.0.jar`).

## One-time manual steps you must run (interactive)
1. Authenticate and choose project:
   - `gcloud auth login`
   - `gcloud config set project <YOUR_PROJECT_ID>`
2. Enable required APIs:
   - `gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com`

## Save Neon secrets in Secret Manager
Run:
- `export PROJECT_ID=<YOUR_PROJECT_ID>`
- `export NEON_USERNAME_VALUE=<YOUR_NEON_USERNAME>`
- `export NEON_PASSWORD_VALUE=<YOUR_NEON_PASSWORD>`
- `./scripts/gcp-secrets.sh`

## Deploy backend to Cloud Run
Run:
- `export PROJECT_ID=<YOUR_PROJECT_ID>`
- `export REGION=<YOUR_GCP_REGION>`
- `export SERVICE_NAME=pgms-backend`
- `export NEON_HOST=<YOUR_NEON_HOST>`
- `export NEON_DATABASE_NAME=<YOUR_NEON_DB>`
- `./scripts/deploy-cloudrun.sh`

## Notes
- Deployment script uses **Cloud Build** to build and push the container, so Docker daemon is not required.
- Update CORS after frontend is deployed:
  - set `app.cors.allowed-origins=https://<your-frontend-domain>`
