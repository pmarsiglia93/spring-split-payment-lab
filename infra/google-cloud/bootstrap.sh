#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-spring-split-payment-lab}"
REGION="${GOOGLE_CLOUD_REGION:-us-central1}"
REPOSITORY="split-payment-lab"
RUNTIME_ACCOUNTS=(orchestrator-runtime payment-runtime split-runtime transfer-runtime)

gcloud config set project "${PROJECT_ID}"

gcloud services enable \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  firebase.googleapis.com \
  firebasehosting.googleapis.com \
  run.googleapis.com \
  secretmanager.googleapis.com

if ! gcloud artifacts repositories describe "${REPOSITORY}" --location "${REGION}" >/dev/null 2>&1; then
  gcloud artifacts repositories create "${REPOSITORY}" \
    --repository-format docker \
    --location "${REGION}" \
    --description "Spring Split Payment Lab images"
fi

for account in "${RUNTIME_ACCOUNTS[@]}"; do
  account_email="${account}@${PROJECT_ID}.iam.gserviceaccount.com"
  if ! gcloud iam service-accounts describe "${account_email}" >/dev/null 2>&1; then
    gcloud iam service-accounts create "${account}" --display-name "${account}"
  fi
done

if ! gcloud secrets describe mongodb-uri >/dev/null 2>&1; then
  gcloud secrets create mongodb-uri --replication-policy automatic
fi

for account in orchestrator-runtime payment-runtime transfer-runtime; do
  gcloud secrets add-iam-policy-binding mongodb-uri \
    --member "serviceAccount:${account}@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role roles/secretmanager.secretAccessor >/dev/null
done

echo "Bootstrap concluído em ${PROJECT_ID}/${REGION}."
echo "Próxima etapa: adicione uma versão ao secret mongodb-uri e execute deploy.sh."
