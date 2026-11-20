#!/usr/bin/env bash
# =====================================================================
# Production deploy (CI'dan SSH orqali yoki qo'lda):  ./scripts/deploy.sh ghcr.io/<org>/<repo>/api:v1.2.3
#   1) backup (postgres + minio)   2) yangi image'ni tortish   3) api'ni yangilash
#   4) health + smoke tekshiruvi   5) muvaffaqiyatsiz bo'lsa — oldingi image'ga avtomatik qaytish (rollback)
# Flyway migratsiyalari faqat oldinga — rollback'da DB eski versiyaga qaytmaydi: migratsiyalar orqaga mos
# (additive) yozilishi shart. DB'ni ham qaytarish kerak bo'lsa: ./scripts/restore.sh backups/<oxirgi>
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

NEW_IMAGE=${1:?usage: deploy.sh <image>}
STATE_FILE=.deployed-image
PREV_IMAGE=$(cat "$STATE_FILE" 2>/dev/null || true)
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}

log() { echo "[deploy $(date -u +%H:%M:%S)] $*"; }

wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while (( SECONDS < deadline )); do
    if [[ "$(docker compose ps api --format '{{.Health}}' 2>/dev/null)" == "healthy" ]] \
       && curl -fsk --max-time 5 https://127.0.0.1/api/v1/public/meta -H "Host: ${SERVER_NAME:-localhost}" >/dev/null; then
      return 0
    fi
    sleep 5
  done
  return 1
}

up() {
  API_IMAGE="$1" docker compose pull api
  API_IMAGE="$1" docker compose up -d --no-build api nginx
}

log "backup"
./scripts/backup.sh

log "deploying $NEW_IMAGE (previous: ${PREV_IMAGE:-none})"
up "$NEW_IMAGE"
if wait_healthy; then
  echo "$NEW_IMAGE" > "$STATE_FILE"
  log "OK — $NEW_IMAGE is healthy"
  exit 0
fi

log "FAILED — api is not healthy within ${HEALTH_TIMEOUT}s"
docker compose logs --tail 100 api || true
if [[ -n "$PREV_IMAGE" ]]; then
  log "rolling back to $PREV_IMAGE"
  up "$PREV_IMAGE"
  wait_healthy && log "rollback OK" || log "ROLLBACK FAILED — manual intervention required"
fi
exit 1
