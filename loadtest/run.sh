#!/usr/bin/env bash
# Yuklama testi: seed -> qidiruv indeksi -> k6 (docker tarmog'ida, API'ga to'g'ridan-to'g'ri) -> natija.
#   PROFILE=smoke|load|stress ./loadtest/run.sh
# Nginx chetlab o'tiladi: u IP bo'yicha 30 r/s cheklaydi, k6 esa bitta IP'dan minglab foydalanuvchini taqlid qiladi.
# Faqat test/staging muhitida! Tozalash: ./loadtest/run.sh cleanup
set -euo pipefail
cd "$(dirname "$0")/.."
PROFILE=${PROFILE:-load}
K6_IMAGE=${K6_IMAGE:-grafana/k6:1.3.0}
psql() { docker compose exec -T postgres psql -U "${POSTGRES_USER:-usta}" -d "${POSTGRES_DB:-usta}" "$@"; }

if [[ "${1:-}" == "cleanup" ]]; then
  psql -q < loadtest/cleanup.sql && echo "Load-test data removed"; exit 0
fi

if [[ "$(psql -tAc "select count(*) from users where phone like '+99877%'")" == "0" ]]; then
  echo "Seeding load-test data..."; psql -q < loadtest/seed.sql
  ADMIN_ID=$(psql -tAc "select u.id from users u join user_roles r on r.user_id=u.id and r.role='ADMIN' limit 1")
  [[ -n "$ADMIN_ID" ]] || { echo "Admin foydalanuvchi kerak (ADMIN_PHONES bilan bir marta kiring)"; exit 1; }
  # reindex — admin tokeni bilan (k6 konteyneri ichida imzolanadi)
  docker run --rm --network usta_default -e JWT_SECRET="${JWT_SECRET:-insecure-local-compose-secret-change-me-0123456789}" \
    -e ADMIN_ID="$ADMIN_ID" -v "$PWD/loadtest/k6:/scripts:ro" "$K6_IMAGE" run -q /scripts/reindex.js
fi
SERVICES=$(psql -tAc "select json_build_object('faucet', md5('svc:faucet-install')::uuid, 'pipe', md5('svc:pipe-leak')::uuid,
  'toilet', md5('svc:toilet-install')::uuid, 'plumbing', md5('cat:plumbing')::uuid)")
psql -qc "select pg_stat_statements_reset()" >/dev/null 2>&1 || true
mkdir -p loadtest/results
OUT="loadtest/results/$(date +%Y%m%dT%H%M%S)-$PROFILE"
echo "Running k6 profile=$PROFILE -> $OUT.json"
docker run --rm --user "$(id -u):$(id -g)" --network usta_default -e PROFILE="$PROFILE" -e DURATION="${DURATION:-5m}" -e SERVICES="$SERVICES" \
  -e JWT_SECRET="${JWT_SECRET:-insecure-local-compose-secret-change-me-0123456789}" \
  -v "$PWD/loadtest/k6:/scripts:ro" -v "$PWD/loadtest/results:/results" \
  "$K6_IMAGE" run --summary-export "/results/$(basename "$OUT").json" /scripts/usta.js | tee "$OUT.txt"
