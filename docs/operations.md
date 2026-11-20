# Operatsion qo'llanma (runbook)

## Deploy va rollback

```bash
git tag v1.2.0 && git push --tags     # CI: build -> alert qoidalari -> Trivy -> image -> deploy
```

Serverda `scripts/deploy.sh <image>` bajariladi:
1. backup;
2. image'ni tortish (pull);
3. `api` va `nginx` ni yangilash;
4. 180 s ichida health va smoke tekshiruvi (`/api/v1/public/meta`).

Muvaffaqiyatsiz bo'lsa, script avtomatik ravishda oldingi image'ga qaytadi (`.deployed-image`). Qo'lda
rollback: `./scripts/deploy.sh ghcr.io/<org>/<repo>/api:v1.1.0`.

- Migratsiyalar faqat oldinga yuradi. Rollback mumkin bo'lishi uchun ular **orqaga mos** yozilishi kerak:
  avval ustun qo'shiladi, keyingi relizda eskisi olib tashlanadi.
- Ilova trafik qabul qilishdan oldin JVM'ni isitadi (~10–15 s, `app.warmup.enabled`); readiness shundan keyin UP
  bo'ladi.
- Graceful shutdown: 20 s, joriy so'rovlar yakunlanadi.

## Backup va restore

```bash
./scripts/backup.sh                                   # backups/<UTC vaqt>/: postgres.dump, minio.tar.gz, SHA256SUMS
./scripts/restore.sh backups/20261119T030000Z         # tasdiq so'raydi; --db-only, --yes
```

Cron (har kecha 03:00): `0 3 * * * cd /opt/usta && ./scripts/backup.sh >> backups/backup.log 2>&1`.
Saqlash muddati `BACKUP_RETENTION_DAYS` (14). Backup'larni serverdan tashqariga (S3 yoki boshqa server)
ham ko'chiring.

Restore'dan keyin tekshiruv: `GET /api/v1/admin/finance/reconciliation` → `"ok": true`.
P5 da sinalgan: to'liq sikl (backup → o'zgartirish → restore) va reconciliation OK.

## Alertlar (Grafana → Alerting → Alert rules → "Prometheus")

| Alert | Nima qilish kerak |
|---|---|
| **ApiDown** | `docker compose ps`, `docker compose logs --tail 200 api`. DB/Redis sog'lommi? `docker compose up -d api` |
| **ApiHighErrorRate** | Loglarda `"level":"ERROR"` va `traceId`. Yaqinda deploy bo'lgan bo'lsa — rollback |
| **ApiHighLatency** | Grafana: qaysi URI? PostgreSQL CPU (`docker stats`), sekin so'rovlar: `select query, mean_exec_time from pg_stat_statements order by total_exec_time desc limit 10` |
| **DbPoolSaturated** | Odatda PostgreSQL CPU chegarasi (`docs/performance.md`). Qisqa muddatli yechim: `PG_CPUS` ni oshirish |
| **JvmHeapHigh** | `API_MEMORY` ni oshirish; takrorlansa heap dump (`jcmd 1 GC.heap_dump /tmp/h.hprof`) |
| **OutboxBacklog / OutboxDeadEvents** | `select event_type, attempts, last_error from outbox_events where published_at is null order by created_at limit 20`. Sabab tuzatilgach: `update outbox_events set attempts = 0, next_attempt_at = now() where id = ...` |
| **FinanceReconciliationMismatch** | `GET /api/v1/admin/finance/reconciliation?from=...&to=...` — `mismatches` ro'yxati. Ledger'ni qo'lda o'zgartirmang (trigger taqiqlaydi). Tuzatish ADJUSTMENT yozuvi orqali |
| **OrdersExpiringTooOften** | Hududda usta yetishmaydimi (Grafana: buyurtmalar oqimi)? Push ishlayaptimi (`PUSH_PROVIDER`)? |
| **ProductionConfigWarnings** | `docker compose logs api \| grep "PRODUCTION CONFIG WARNING"` → `.env` ni tuzatib qayta ishga tushirish |

Bildirishnomalarni Telegram yoki email'ga yuborish kerak bo'lsa: Grafana → Alerting → Contact points
(qoidalar o'zgarmaydi).

## Masshtablash

`docs/performance.md` ga qarang. Birinchi chegara — PostgreSQL CPU (qidiruv geo-so'rovi). Navbat bilan:
1. `PG_CPUS` va server CPU'sini oshirish;
2. qidiruv uchun read-replica;
3. API'ni bir nechta instansiyada ishga tushirish. Chat Redis pub/sub orqali, rejalashtirilgan vazifalar
   ShedLock orqali ishlaydi, shuning uchun ilova bunga tayyor. Nginx `upstream` ga serverlarni qo'shish kifoya.

## Yuklama testi

```bash
PROFILE=smoke ./loadtest/run.sh      # 30 s
PROFILE=load ./loadtest/run.sh       # 5 daq, maqsadli yuklama (~160 RPS)
PROFILE=stress ./loadtest/run.sh     # 8 daq, 100 -> 1200 RPS (chegarani topish)
./loadtest/run.sh cleanup            # +99877... test ma'lumotlarini o'chirish
```

Faqat test yoki staging muhitida ishlating: script standart lokal JWT siridan foydalanadi va
2 500 ta test foydalanuvchi yaratadi.
