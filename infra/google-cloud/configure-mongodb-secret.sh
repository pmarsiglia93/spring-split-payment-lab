#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="${GOOGLE_CLOUD_PROJECT:-spring-split-payment-lab}"
gcloud config set project "${PROJECT_ID}"

urlencode() {
  local value="$1"
  local encoded=""
  local character
  local hexadecimal
  local index

  LC_ALL=C
  for ((index = 0; index < ${#value}; index++)); do
    character="${value:index:1}"
    case "${character}" in
      [a-zA-Z0-9.~_-]) encoded+="${character}" ;;
      *)
        printf -v hexadecimal '%%%02X' "'${character}"
        encoded+="${hexadecimal}"
        ;;
    esac
  done
  printf '%s' "${encoded}"
}

read -r -p "Cole a URI modelo do Atlas, mantendo <db_username> e <db_password>: " MONGODB_ATLAS_URI
echo

if [[ -z "${MONGODB_ATLAS_URI}" ]]; then
  echo "A URI não pode ficar vazia." >&2
  exit 1
fi

if [[ "${MONGODB_ATLAS_URI}" == *"<db_username>"* || "${MONGODB_ATLAS_URI}" == *"<db_password>"* ]]; then
  read -r -p "Usuário do banco: " MONGODB_ATLAS_USERNAME
  read -r -s -p "Senha do banco (não será exibida): " MONGODB_ATLAS_PASSWORD
  echo

  if [[ -z "${MONGODB_ATLAS_USERNAME}" || -z "${MONGODB_ATLAS_PASSWORD}" ]]; then
    echo "Usuário e senha não podem ficar vazios." >&2
    exit 1
  fi

  ENCODED_USERNAME="$(urlencode "${MONGODB_ATLAS_USERNAME}")"
  ENCODED_PASSWORD="$(urlencode "${MONGODB_ATLAS_PASSWORD}")"
  MONGODB_ATLAS_URI="${MONGODB_ATLAS_URI//<db_username>/${ENCODED_USERNAME}}"
  MONGODB_ATLAS_URI="${MONGODB_ATLAS_URI//<db_password>/${ENCODED_PASSWORD}}"
fi

if [[ "${MONGODB_ATLAS_URI}" != mongodb+srv://* || "${MONGODB_ATLAS_URI}" == *"<"* ]]; then
  echo "A URI informada não parece uma conexão válida do MongoDB Atlas." >&2
  exit 1
fi

printf '%s' "${MONGODB_ATLAS_URI}" | gcloud secrets versions add mongodb-uri --data-file=-
unset MONGODB_ATLAS_URI MONGODB_ATLAS_USERNAME MONGODB_ATLAS_PASSWORD
unset ENCODED_USERNAME ENCODED_PASSWORD

echo "Secret mongodb-uri atualizado sem gravar a credencial no repositório."
