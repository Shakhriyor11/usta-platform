#!/usr/bin/env bash
# PostgreSQL (pg_dump, custom format) va MinIO (fayllar) zaxira nusxasi.
#
#   ./scripts/backup.sh                 # ./backups/<timestamp>/ ga yozadi
#   BACKUP_DIR=/mnt/backup ./scripts/backup.sh
#
# BACKUP_PASSPHRASE berilsa (.env), fayllar AES-256 bilan shifrlanib yoziladi (*.enc) — ochiq nusxa diskka
# tushmaydi. Nusxada shaxsiy ma'lumotlar bor: production'da parolni albatta bering.
#
# Cron misoli (har kuni 03:00):
#   0 3 * * * cd /opt/usta && ./scripts/backup.sh >> backups/backup.log 2>&1
set -euo pipefail
umask 077 # nusxalarni faqat egasi o'qiy oladi

cd "$(dirname "$0")/.."
CLI_PASSPHRASE="${BACKUP_PASSPHRASE:-}"
CLI_BACKUP_DIR="${BACKUP_DIR:-}"
[ -f .env ] && set -a && . ./.env && set +a
# buyruq qatorida (environment) berilgan qiymatlar .env dagidan ustun
BACKUP_PASSPHRASE="${CLI_PASSPHRASE:-${BACKUP_PASSPHRASE:-}}"
BACKUP_DIR="${CLI_BACKUP_DIR:-${BACKUP_DIR:-}}"

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
DB="${POSTGRES_DB:-usta}"
DB_USER="${POSTGRES_USER:-usta}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/${TS}"

# stdin -> $1 (parol bo'lsa $1.enc)
save() {
  if [ -n "${BACKUP_PASSPHRASE:-}" ]; then
    BACKUP_PASSPHRASE="${BACKUP_PASSPHRASE}" openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
      -pass env:BACKUP_PASSPHRASE -out "$1.enc"
  else
    cat > "$1"
  fi
}

mkdir -p "${TARGET}"
echo "[backup] ${TS} -> ${TARGET}"
[ -n "${BACKUP_PASSPHRASE:-}" ] || echo "[backup] DIQQAT: BACKUP_PASSPHRASE berilmagan — nusxa shifrlanmaydi"

# 1) PostgreSQL — konteyner ichida pg_dump, natija host'ga oqim orqali
docker compose exec -T postgres pg_dump -U "${DB_USER}" -d "${DB}" -Fc --no-owner --no-privileges \
  | save "${TARGET}/postgres.dump"
echo "[backup] postgres: $(du -h "${TARGET}"/postgres.dump* | cut -f1)"

# 2) MinIO — data volume'ini tar qilib olamiz (bucket'lar + metadata)
if docker compose ps --status running --services | grep -qx minio; then
  docker run --rm --volumes-from "$(docker compose ps -q minio)" alpine:3.22 tar czf - -C /data . \
    | save "${TARGET}/minio.tar.gz"
  echo "[backup] minio: $(du -h "${TARGET}"/minio.tar.gz* | cut -f1)"
else
  echo "[backup] minio ishlamayapti — o'tkazib yuborildi"
fi

# 3) Tekshiruv summasi
( cd "${TARGET}" && sha256sum ./* > SHA256SUMS )

# 4) Eski nusxalarni o'chirish
find "${BACKUP_DIR}" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime "+${RETENTION_DAYS}" -print -exec rm -rf {} +

echo "[backup] tayyor: ${TARGET}"
