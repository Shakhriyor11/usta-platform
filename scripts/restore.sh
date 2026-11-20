#!/usr/bin/env bash
# backup.sh yaratgan nusxadan tiklash.
#
#   ./scripts/restore.sh backups/20261118T030000Z            # postgres + minio
#   ./scripts/restore.sh backups/20261118T030000Z --db-only
#
# DIQQAT: joriy ma'lumotlar bazasi va fayllar ALMASHTIRILADI. API tiklash vaqtida to'xtatiladi.
set -euo pipefail

cd "$(dirname "$0")/.."
[ -f .env ] && set -a && . ./.env && set +a

SRC="${1:?Foydalanish: $0 <backup-papka> [--db-only] [--yes]}"
shift
DB_ONLY=false; ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --db-only) DB_ONLY=true ;;
    --yes) ASSUME_YES=true ;;
    *) echo "Noma'lum parametr: $arg"; exit 2 ;;
  esac
done

DB="${POSTGRES_DB:-usta}"
DB_USER="${POSTGRES_USER:-usta}"

[ -f "${SRC}/postgres.dump" ] || { echo "${SRC}/postgres.dump topilmadi"; exit 1; }
( cd "${SRC}" && sha256sum -c --quiet SHA256SUMS ) || { echo "Checksum mos kelmadi!"; exit 1; }

if [ "${ASSUME_YES}" != true ]; then
  read -r -p "'${DB}' bazasi ${SRC} dan tiklanadi, joriy ma'lumotlar o'chadi. Davom etilsinmi? [yes/N] " ans
  [ "${ans}" = "yes" ] || { echo "Bekor qilindi"; exit 1; }
fi

echo "[restore] api to'xtatilmoqda"
docker compose stop api nginx || true

echo "[restore] postgres"
docker compose exec -T postgres psql -U "${DB_USER}" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB}' AND pid <> pg_backend_pid();" \
  -c "DROP DATABASE IF EXISTS \"${DB}\";" \
  -c "CREATE DATABASE \"${DB}\" OWNER \"${DB_USER}\";"
docker compose exec -T postgres pg_restore -U "${DB_USER}" -d "${DB}" --no-owner --no-privileges --exit-on-error \
  < "${SRC}/postgres.dump"

if [ "${DB_ONLY}" != true ] && [ -f "${SRC}/minio.tar.gz" ]; then
  echo "[restore] minio"
  docker compose stop minio
  docker run --rm --volumes-from "$(docker compose ps -aq minio)" -v "$(realpath "${SRC}")":/backup:ro \
    alpine:3.22 sh -c 'find /data -mindepth 1 -delete && tar xzf /backup/minio.tar.gz -C /data'
  docker compose start minio
fi

echo "[restore] api ishga tushirilmoqda"
docker compose up -d api nginx
echo "[restore] tayyor"
