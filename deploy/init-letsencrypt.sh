#!/usr/bin/env bash
# 인증서를 처음 한 번 발급받는다. EC2 의 ~/app 에서 실행한다.
#
#   ./init-letsencrypt.sh
#
# 두 번째부터는 쓰지 않는다. 갱신은 certbot renew 다(docker-compose.prod.yml 주석 참고).
#
# 왜 스크립트가 필요한가:
#   nginx 설정의 443 블록이 인증서 파일을 가리킨다. 파일이 없으면 nginx 가 기동에
#   실패한다. 그런데 certbot 의 소유 확인(HTTP-01)은 80 번으로 들어와야 하므로
#   nginx 가 떠 있어야 한다. 서로를 기다리는 관계다.
#
#   그래서 자체 서명 인증서를 먼저 그 자리에 놓아 nginx 를 띄우고, 진짜 인증서를
#   받은 뒤 갈아끼운다.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE=docker-compose.prod.yml
COMPOSE="docker compose -f $COMPOSE_FILE"

if [ ! -f .env ]; then
  echo ".env 가 없다. .env.prod.example 을 복사해 값을 채운다." >&2
  exit 1
fi

# .env 를 읽어 SERVER_NAME 과 LETSENCRYPT_EMAIL 을 가져온다.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${SERVER_NAME:?.env 에 SERVER_NAME 을 채운다 (예: api.example.com)}"
: "${LETSENCRYPT_EMAIL:?.env 에 LETSENCRYPT_EMAIL 을 채운다 (만료 안내를 받을 주소)}"

# --staging 을 붙여 먼저 연습할 수 있다. Let's Encrypt 는 같은 도메인에 주 5회만
# 발급해 주므로, 설정을 확인하는 단계에서는 스테이징으로 돌리는 편이 안전하다.
#   STAGING=1 ./init-letsencrypt.sh
STAGING_ARG=""
if [ "${STAGING:-0}" != "0" ]; then
  STAGING_ARG="--staging"
  echo "스테이징 모드. 브라우저가 신뢰하지 않는 인증서를 받는다(설정 확인용)."
fi

CERT_PATH="/etc/letsencrypt/live/${SERVER_NAME}"

echo "==> 1/4 자체 서명 인증서를 임시로 놓는다 — nginx 를 띄우기 위해서다"
$COMPOSE run --rm --entrypoint sh certbot -c "
  mkdir -p '${CERT_PATH}' &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '${CERT_PATH}/privkey.pem' \
    -out '${CERT_PATH}/fullchain.pem' \
    -subj '/CN=${SERVER_NAME}'
"

echo "==> 2/4 nginx 기동"
$COMPOSE up -d nginx

# nginx 가 80 번을 받을 때까지 기다린다. 바로 certbot 을 부르면 연결이 거부된다.
echo "    80 번 응답 대기"
for _ in $(seq 1 30); do
  if curl -fsS -o /dev/null "http://localhost/.well-known/acme-challenge/" 2>/dev/null \
    || curl -fsS -o /dev/null -w '%{http_code}' "http://localhost/" 2>/dev/null | grep -q '^[0-9]'; then
    break
  fi
  sleep 2
done

echo "==> 3/4 임시 인증서를 지우고 진짜 인증서를 받는다"
# 지우지 않으면 certbot 이 "이미 인증서가 있다" 며 갱신으로 처리해 발급이 어긋난다.
$COMPOSE run --rm --entrypoint sh certbot -c "rm -rf '${CERT_PATH}' /etc/letsencrypt/archive/${SERVER_NAME} /etc/letsencrypt/renewal/${SERVER_NAME}.conf"

$COMPOSE run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  $STAGING_ARG \
  -d "${SERVER_NAME}" \
  --email "${LETSENCRYPT_EMAIL}" \
  --agree-tos \
  --no-eff-email \
  --non-interactive

echo "==> 4/4 nginx 다시 읽기"
$COMPOSE exec -T nginx nginx -s reload

echo
echo "발급 완료. 확인:"
echo "  curl -I https://${SERVER_NAME}/actuator/health"
echo
echo "갱신을 crontab 에 걸어 둔다(인증서 수명 90일):"
echo "  0 4 * * 1 cd $(pwd) && docker compose -f $COMPOSE_FILE run --rm certbot renew --quiet && docker compose -f $COMPOSE_FILE exec -T nginx nginx -s reload"
