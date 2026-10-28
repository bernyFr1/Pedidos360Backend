#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f infra/apps/.env ]; then
  echo "Falta infra/apps/.env (TENANT_ID y CLIENT_ID_API)." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source infra/apps/.env
set +a

if [ -z "${TENANT_ID:-}" ] || [ -z "${CLIENT_ID_API:-}" ]; then
  echo "TENANT_ID y CLIENT_ID_API son obligatorios en infra/apps/.env" >&2
  exit 1
fi

export AWS_ENDPOINT_URL="http://localhost:4566"
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="us-east-1"

BFF_URL="${BFF_URL:-http://host.docker.internal:8080}"
STAGE_NAME="Desarrollo"
API_NAME="pedidos360-api"

docker compose -f infra/floci/docker-compose.yml up -d floci

for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:4566/_localstack/health 2>/dev/null || true)" = "200" ]; then
    break
  fi
  sleep 2
done

EXISTING_APIS="$(aws apigatewayv2 get-apis \
  --query "Items[?Name=='${API_NAME}'].ApiId" \
  --output text)"
for api_id in $EXISTING_APIS; do
  aws apigatewayv2 delete-api --api-id "$api_id" >/dev/null
done

API_ID="$(aws apigatewayv2 create-api \
  --name "$API_NAME" \
  --protocol-type HTTP \
  --cors-configuration '{"AllowOrigins":["http://localhost:4200"],"AllowMethods":["GET","POST","PUT","DELETE","OPTIONS"],"AllowHeaders":["Authorization","Content-Type","ngrok-skip-browser-warning"],"ExposeHeaders":["Content-Type"]}' \
  --query ApiId --output text)"

INTEGRATION_ID="$(aws apigatewayv2 create-integration \
  --api-id "$API_ID" \
  --integration-type HTTP_PROXY \
  --integration-uri "${BFF_URL}/{proxy}" \
  --integration-method ANY \
  --payload-format-version '1.0' \
  --query IntegrationId --output text)"

AUTHORIZER_ID="$(aws apigatewayv2 create-authorizer \
  --api-id "$API_ID" \
  --name entra-jwt \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Issuer=https://login.microsoftonline.com/${TENANT_ID}/v2.0,Audience=${CLIENT_ID_API}" \
  --query AuthorizerId --output text)"

ROUTE_ID="$(aws apigatewayv2 create-route \
  --api-id "$API_ID" \
  --route-key 'ANY /{proxy+}' \
  --target "integrations/${INTEGRATION_ID}" \
  --authorizer-id "$AUTHORIZER_ID" \
  --query RouteId --output text)"

DEPLOYMENT_ID="$(aws apigatewayv2 create-deployment \
  --api-id "$API_ID" \
  --query DeploymentId --output text)"

aws apigatewayv2 create-stage \
  --api-id "$API_ID" \
  --stage-name "$STAGE_NAME" \
  --deployment-id "$DEPLOYMENT_ID" >/dev/null

API_ENDPOINT="$(aws apigatewayv2 get-apis \
  --query "Items[?Name=='${API_NAME}'].ApiEndpoint | [0]" \
  --output text)"
API_HOST="${API_ENDPOINT#*://}"

FLOCI_API_HOST="$API_HOST" docker compose -f infra/floci/docker-compose.yml up -d floci-gw

cat > /tmp/floci-api.env <<ENV
API_ID=${API_ID}
API_HOST=${API_HOST}
STAGE_NAME=${STAGE_NAME}
AUTHORIZER_ID=${AUTHORIZER_ID}
INTEGRATION_ID=${INTEGRATION_ID}
ROUTE_ID=${ROUTE_ID}
FLOCI_API_BASE_URL=http://localhost:8088/${STAGE_NAME}
ENV

echo "Floci API Gateway listo."
echo "API_ID=${API_ID}"
echo "API_HOST=${API_HOST}"
echo "FLOCI_API_BASE_URL=http://localhost:8088/${STAGE_NAME}"
echo "Configura el frontend: apiBaseUrl='http://localhost:8088/${STAGE_NAME}'"
