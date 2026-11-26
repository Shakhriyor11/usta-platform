#!/usr/bin/env bash
# backup.sh yaratgan nusxadan tiklash.
#
#   ./scripts/restore.sh backups/20261118T030000Z            # postgres + minio
#   ./scripts/restore.sh backups/20261118T030000Z --db-only
#
# Shifrlangan nusxa (*.enc) uchun BACKUP_PASSPHRASE (.env yoki environment) kerak.
# DIQQAT: joriy ma'lumotlar bazasi va fayllar ALMASHTIRILADI. API tiklash vaqtida to'xtatiladi.
set -euo pipefail

cd "$(dirname "$0")/.."
CLI_PASSPHRASE="${BACKUP_PASSPHRASE:-}"
[ -f .env ] && set -a && . ./.env && set +a
# buyruq qatorida (environment) berilgan parol .env dagidan ustun
BACKUP_PASSPHRASE="${CLI_PASSPHRASE:-${BACKUP_PASSPHRASE:-}}"

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

# $1 faylining ochiq mazmunini stdout'ga yozadi ($1.enc bo'lsa — deshifrlab)
load() {
  if [ -f "$1.enc" ]; then
    BACKUP_PASSPHRASE="${BACKUP_PASSPHRASE}" openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
      -pass env:BACKUP_PASSPHRASE -in "$1.enc"
  else
    cat "$1"
  fi
}

[ -f "${SRC}/postgres.dump" ] || [ -f "${SRC}/postgres.dump.enc" ] || { echo "${SRC}/postgres.dump topilmadi"; exit 1; }
( cd "${SRC}" && sha256sum -c --quiet SHA256SUMS ) || { echo "Checksum mos kelmadi!"; exit 1; }

# Hech narsa o'chirilmasidan oldin: shifrlangan fayllar shu parol bilan to'liq ochilishini tekshiramiz
for f in postgres.dump minio.tar.gz; do
  if [ -f "${SRC}/$f.enc" ]; then
    [ -n "${BACKUP_PASSPHRASE:-}" ] || { echo "$f.enc shifrlangan — BACKUP_PASSPHRASE bering"; exit 1; }
    load "${SRC}/$f" > /dev/null || { echo "$f.enc ochilmadi — parol noto'g'ri yoki fayl buzilgan"; exit 1; }
  fi
done

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
load "${SRC}/postgres.dump" \
  | docker compose exec -T postgres pg_restore -U "${DB_USER}" -d "${DB}" --no-owner --no-privileges --exit-on-error

if [ "${DB_ONLY}" != true ] && { [ -f "${SRC}/minio.tar.gz" ] || [ -f "${SRC}/minio.tar.gz.enc" ]; }; then
  echo "[restore] minio"
  docker compose stop minio
  load "${SRC}/minio.tar.gz" \
    | docker run --rm -i --volumes-from "$(docker compose ps -aq minio)" \
        alpine:3.22 sh -c 'find /data -mindepth 1 -delete && tar xzf - -C /data'
  docker compose start minio
fi

echo "[restore] api ishga tushirilmoqda"
docker compose up -d api nginx
echo "[restore] tayyor"
