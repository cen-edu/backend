#!/usr/bin/env bash
set -Eeuo pipefail

# Reproducible local full problem-search backfill.
# The application owns normalization, validation, retry, and idempotency; this
# script only starts the required services and waits for the durable queue to drain.

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi
if ! grep -qE '^OPENAI_API_KEY=.+$' .env 2>/dev/null && [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is required in .env or the environment" >&2
  exit 1
fi

docker compose up -d postgres

server_port="${BACKFILL_SERVER_PORT:-18080}"
poll_seconds="${BACKFILL_POLL_SECONDS:-10}"
stable_polls="${BACKFILL_STABLE_POLLS:-3}"

APP_PROBLEM_RAG_ENABLED=true \
APP_PROBLEM_RAG_INDEXING_ENABLED=true \
APP_PROBLEM_RAG_INDEXING_WORKER_DELAY=2s \
APP_PROBLEM_RAG_INDEXING_BACKFILL_DELAY=2s \
APP_PROBLEM_RAG_INDEXING_BATCH_SIZE=20 \
SERVER_PORT="$server_port" \
bash gradlew bootRun >"${BACKFILL_LOG_FILE:-/tmp/cen-edu-problem-backfill.log}" 2>&1 &
backend_pid=$!

cleanup() {
  kill "$backend_pid" 2>/dev/null || true
  wait "$backend_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Waiting for backend and Flyway..."
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${server_port}/actuator/health" >/dev/null 2>&1; then break; fi
  sleep 1
done

stable=0
previous=""
while true; do
  read -r target ready pending failed tasks <<EOF
$(docker compose exec -T postgres psql -U cen -d cen_edu -Atc "
select (select count(*) from problem_question where deleted_at is null and question_type <> 'ESSAY'),
       (select count(*) from problem_search_index where index_status='READY' and deleted=false),
       (select count(*) from problem_search_index_task where status in ('PENDING','PROCESSING','RETRY_WAIT')),
       (select count(*) from problem_search_index_task where status='FAILED'),
       (select count(*) from problem_search_index_task);")
EOF
  echo "target=${target} ready=${ready} pending=${pending} failed=${failed} tasks=${tasks}"
  if [[ "$failed" != "0" ]]; then
    echo "Backfill stopped because failed tasks were detected. See ${BACKFILL_LOG_FILE:-/tmp/cen-edu-problem-backfill.log}" >&2
    exit 1
  fi
  current="${ready}:${pending}:${tasks}"
  if [[ "$pending" == "0" && "$current" == "$previous" ]]; then
    stable=$((stable + 1))
  else
    stable=0
  fi
  if [[ "$stable" -ge "$stable_polls" ]]; then
    echo "Backfill queue is drained. Non-reusable rows remain excluded by validation."
    break
  fi
  previous="$current"
  sleep "$poll_seconds"
done
