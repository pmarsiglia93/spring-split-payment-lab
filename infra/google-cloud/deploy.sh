#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-spring-split-payment-lab}"
REGION="${GOOGLE_CLOUD_REGION:-us-central1}"
REPOSITORY="split-payment-lab"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}"

gcloud config set project "${PROJECT_ID}"

if ! gcloud secrets versions access latest --secret mongodb-uri >/dev/null 2>&1; then
  echo "O secret mongodb-uri ainda não possui uma versão acessível." >&2
  exit 1
fi

for service in payment-service split-service transfer-service orchestrator-service; do
  gcloud builds submit "${service}" --tag "${REGISTRY}/${service}:${IMAGE_TAG}"
done

gcloud run deploy payment-service \
  --image "${REGISTRY}/payment-service:${IMAGE_TAG}" \
  --region "${REGION}" \
  --service-account "payment-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --no-allow-unauthenticated \
  --min 0 --max 1 --cpu 1 --memory 512Mi --concurrency 20 --timeout 30 \
  --set-env-vars MONGODB_DATABASE=paymentdb \
  --set-secrets MONGODB_URI=mongodb-uri:latest

gcloud run deploy split-service \
  --image "${REGISTRY}/split-service:${IMAGE_TAG}" \
  --region "${REGION}" \
  --service-account "split-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --no-allow-unauthenticated \
  --min 0 --max 1 --cpu 1 --memory 512Mi --concurrency 20 --timeout 30

gcloud run deploy transfer-service \
  --image "${REGISTRY}/transfer-service:${IMAGE_TAG}" \
  --region "${REGION}" \
  --service-account "transfer-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --no-allow-unauthenticated \
  --min 0 --max 1 --cpu 1 --memory 512Mi --concurrency 20 --timeout 30 \
  --set-env-vars MONGODB_DATABASE=transferdb \
  --set-secrets MONGODB_URI=mongodb-uri:latest

PAYMENT_URL="$(gcloud run services describe payment-service --region "${REGION}" --format 'value(status.url)')"
SPLIT_URL="$(gcloud run services describe split-service --region "${REGION}" --format 'value(status.url)')"
TRANSFER_URL="$(gcloud run services describe transfer-service --region "${REGION}" --format 'value(status.url)')"

for service in payment-service split-service transfer-service; do
  gcloud run services add-iam-policy-binding "${service}" \
    --region "${REGION}" \
    --member "serviceAccount:orchestrator-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role roles/run.invoker >/dev/null
done

gcloud run deploy orchestrator-service \
  --image "${REGISTRY}/orchestrator-service:${IMAGE_TAG}" \
  --region "${REGION}" \
  --service-account "orchestrator-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --allow-unauthenticated \
  --min 0 --max 1 --cpu 1 --memory 512Mi --concurrency 20 --timeout 60 \
  --set-env-vars "SERVICE_AUTH_ENABLED=true,DEMO_RATE_LIMIT_ENABLED=true,DEMO_RATE_LIMIT_REQUESTS_PER_MINUTE=30,PAYMENT_SERVICE_URL=${PAYMENT_URL},SPLIT_SERVICE_URL=${SPLIT_URL},TRANSFER_SERVICE_URL=${TRANSFER_URL},MONGODB_DATABASE=orchestratordb" \
  --set-secrets MONGODB_URI=mongodb-uri:latest

echo "Cloud Run publicado. Agora execute:"
echo "  npm --prefix frontend ci"
echo "  npm --prefix frontend run build"
echo "  firebase deploy --only hosting --project ${PROJECT_ID}"
