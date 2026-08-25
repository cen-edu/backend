#!/usr/bin/env bash
# EC2 에서 실행한다. 새 이미지 태그를 받아 서비스를 갈아끼운다.
#
#   ./deploy.sh 1.0.1
#
# 프론트는 Vercel 이 배포한다. 여기서 다루는 것은 백엔드 이미지뿐이다.
#
# .env 의 APP_TAG 를 바꿔 쓰고, 실패하면 이전 태그로 되돌린다.
set -euo pipefail

cd "$(dirname "$0")"

if [ $# -ne 1 ]; then
  echo "사용법: $0 <백엔드-태그>   예: $0 1.0.1" >&2
  exit 1
fi

NEW_TAG="$1"
COMPOSE_FILE=docker-compose.prod.yml

if [ ! -f .env ]; then
  echo ".env 가 없다. .env.prod.example 을 복사해 값을 채운다." >&2
  exit 1
fi

# 롤백할 때 필요하다. 실패해도 이 파일만 되돌리면 이전 태그로 돌아간다.
cp .env .env.bak

# GNU sed. Amazon Linux 2023 기본 sed 다.
echo "백엔드 $(grep -E '^APP_TAG=' .env | cut -d= -f2-) -> ${NEW_TAG}"
sed -i -E "s|^APP_TAG=.*|APP_TAG=${NEW_TAG}|" .env

docker compose -f "$COMPOSE_FILE" pull
docker compose -f "$COMPOSE_FILE" up -d

echo "헬스체크 대기 중..."
for i in $(seq 1 40); do
  status="$(docker inspect --format '{{.State.Health.Status}}' cen-edu-backend 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    echo "배포 완료"
    # 컨테이너 안에서 직접 확인한다. 밖에서 도메인으로 부르면 DNS·인증서 문제까지
    # 섞여 들어와, 앱이 뜬 것인지 앞단이 문제인지 구분되지 않는다.
    docker compose -f "$COMPOSE_FILE" exec -T backend \
      curl -fs http://localhost:8080/actuator/health && echo
    # 갈아끼운 뒤 남는 이전 이미지가 루트 볼륨을 채운다. 태그가 붙지 않은 것만 지운다.
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "기동 실패. 로그를 확인하고 이전 태그로 되돌린다:" >&2
docker compose -f "$COMPOSE_FILE" logs --tail 100 backend >&2
echo "  cp .env.bak .env && docker compose -f $COMPOSE_FILE up -d" >&2
exit 1
