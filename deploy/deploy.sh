#!/usr/bin/env bash
# EC2 에서 실행한다. 새 이미지 태그를 받아 서비스를 갈아끼운다.
#
#   ./deploy.sh 1.0.1            백엔드만 교체
#   ./deploy.sh 1.0.1 2.3.0      백엔드와 프론트를 함께 교체
#   ./deploy.sh - 2.3.0          프론트만 교체(백엔드 태그는 그대로 둔다)
#
# .env 의 APP_TAG / FRONTEND_TAG 를 바꿔 쓰고, 실패하면 이전 태그로 되돌린다.
set -euo pipefail

cd "$(dirname "$0")"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "사용법: $0 <백엔드-태그|-> [프론트-태그]   예: $0 1.0.1 2.3.0" >&2
  exit 1
fi

NEW_TAG="$1"
NEW_FRONTEND_TAG="${2:-}"
COMPOSE_FILE=docker-compose.prod.yml

if [ ! -f .env ]; then
  echo ".env 가 없다. .env.prod.example 을 복사해 값을 채운다." >&2
  exit 1
fi

# 롤백할 때 필요하다. 실패해도 이 파일만 되돌리면 이전 태그로 돌아간다.
cp .env .env.bak

# GNU sed. Amazon Linux 2023 기본 sed 다.
if [ "$NEW_TAG" != "-" ]; then
  echo "백엔드 $(grep -E '^APP_TAG=' .env | cut -d= -f2-) -> ${NEW_TAG}"
  sed -i -E "s|^APP_TAG=.*|APP_TAG=${NEW_TAG}|" .env
fi
if [ -n "$NEW_FRONTEND_TAG" ]; then
  echo "프론트 $(grep -E '^FRONTEND_TAG=' .env | cut -d= -f2-) -> ${NEW_FRONTEND_TAG}"
  sed -i -E "s|^FRONTEND_TAG=.*|FRONTEND_TAG=${NEW_FRONTEND_TAG}|" .env
fi

docker compose -f "$COMPOSE_FILE" pull
docker compose -f "$COMPOSE_FILE" up -d

echo "헬스체크 대기 중..."
for i in $(seq 1 40); do
  status="$(docker inspect --format '{{.State.Health.Status}}' cen-edu-backend 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    echo "배포 완료"
    curl -fs http://localhost/actuator/health && echo
    curl -fs -o /dev/null -w '프론트 응답: %{http_code}
' http://localhost/
    # 갈아끼운 뒤 남는 이전 이미지가 8GB 루트 볼륨을 채운다. 태그가 붙지 않은 것만 지운다.
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "기동 실패. 로그를 확인하고 이전 태그로 되돌린다:" >&2
docker compose -f "$COMPOSE_FILE" logs --tail 100 backend >&2
echo "  cp .env.bak .env && docker compose -f $COMPOSE_FILE up -d" >&2
exit 1
