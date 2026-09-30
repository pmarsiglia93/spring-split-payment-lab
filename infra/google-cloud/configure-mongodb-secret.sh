#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-spring-split-payment-lab}"
gcloud config set project "${PROJECT_ID}"

read -r -s -p "Cole a URI do MongoDB Atlas (ela não será exibida): " MONGODB_ATLAS_URI
echo

if [[ -z "${MONGODB_ATLAS_URI}" ]]; then
  echo "A URI não pode ficar vazia." >&2
  exit 1
fi

printf '%s' "${MONGODB_ATLAS_URI}" | gcloud secrets versions add mongodb-uri --data-file=-
unset MONGODB_ATLAS_URI

echo "Secret mongodb-uri atualizado sem gravar a credencial no repositório."
