#!/usr/bin/env bash
# PostgreSQL (pg_dump, custom format) va MinIO (fayllar) zaxira nusxasi.
#
#   ./scripts/backup.sh                 # ./backups/<timestamp>/ ga yozadi
#   BACKUP_DIR=/mnt/backup ./scripts/backup.sh
#
# Cron misoli (har kuni 03:00):
#   0 3 * * * cd /opt/usta && ./scripts/backup.sh >> backups/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."
[ -f .env ] && set -a && . ./.env && set +a

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
DB="${POSTGRES_DB:-usta}"
DB_USER="${POSTGRES_USER:-usta}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/${TS}"

mkdir -p "${TARGET}"
echo "[backup] ${TS} -> ${TARGET}"

# 1) PostgreSQL — konteyner ichida pg_dump, natija host'ga oqim orqali
docker compose exec -T postgres pg_dump -U "${DB_USER}" -d "${DB}" -Fc --no-owner --no-privileges \
  > "${TARGET}/postgres.dump"
echo "[backup] postgres: $(du -h "${TARGET}/postgres.dump" | cut -f1)"

# 2) MinIO — data volume'ini tar qilib olamiz (bucket'lar + metadata)
if docker compose ps --status running --services | grep -qx minio; then
  docker run --rm --volumes-from "$(docker compose ps -q minio)" -v "$(realpath "${TARGET}")":/backup \
    alpine:3.22 tar czf /backup/minio.tar.gz -C /data .
  echo "[backup] minio: $(du -h "${TARGET}/minio.tar.gz" | cut -f1)"
else
  echo "[backup] minio ishlamayapti — o'tkazib yuborildi"
fi

# 3) Tekshiruv summasi
( cd "${TARGET}" && sha256sum ./* > SHA256SUMS )

# 4) Eski nusxalarni o'chirish
find "${BACKUP_DIR}" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime "+${RETENTION_DAYS}" -print -exec rm -rf {} +

echo "[backup] tayyor: ${TARGET}"
