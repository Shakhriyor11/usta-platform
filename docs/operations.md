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

- **V15 migratsiyasi istisno**: `chats` dagi `uq_chats_order` cheklovi almashgan. V15 qo'llangach undan oldingi
  image'ga qaytilsa, yangi chat yaratish ishlamaydi (qolgan funksiyalar ishlaydi). Bu relizdan orqaga qaytish
  kerak bo'lsa — bazani ham tiklang yoki oldinga tuzating.
- **V16 migratsiyasi istisno**: `SUPER_ADMIN` roli yozilgach (super admin birinchi marta kirgach) yoki
  foydalanuvchi o'chirilgach, V16 dan oldingi image ishlamaydi (noma'lum rol/holat). Orqaga qaytish kerak bo'lsa —
  bazani deploy oldidagi backup'dan tiklang. `deploy.sh` ning avtomatik rollback'i bu relizda shu cheklovga ega.
- `.env` da `JWT_SECRET` namunaviy/bo'sh yoki `OTP_FIXED_CODE` berilgan bo'lsa, api ishga tushmaydi.

## Adminlar

1. `.env`: `SUPER_ADMIN_PHONES=+998...` (eski `ADMIN_PHONES` ham o'qiladi) → api'ni qayta ishga tushiring → shu
   raqam bilan kiring (allaqachon kirgan bo'lsa, token yangilanganda rol qo'shiladi).
2. Adminlar: `POST /api/v1/admin/admins` `{"phone":"+998...","permissions":["CATALOG","ORDERS"]}`; huquqlarni
   almashtirish — `PUT /admins/{id}`, olib tashlash — `DELETE /admins/{id}`. Huquqlar ro'yxati —
   `GET /admins/permissions`.
3. **V16 dan keyin** bazadagi eski `ADMIN` yozuvlarida hech qanday huquq bo'lmaydi. `SUPER_ADMIN_PHONES` dagilar
   super admin bo'ladi; qolganlarni `GET /admins` da ko'rib, kerakligiga huquq bering, qolganini olib tashlang.
4. Super adminni almashtirish: ro'yxatni o'zgartirib qayta ishga tushiring. Eski raqam keyingi refresh'da (ko'pi
   bilan 15 daqiqa) barcha admin huquqlarini yo'qotadi. Zudlik kerak bo'lsa, `JWT_SECRET` ni almashtiring (barcha
   access tokenlar bekor bo'ladi).

## Backup va restore

```bash
./scripts/backup.sh                                   # backups/<UTC vaqt>/: postgres.dump, minio.tar.gz, SHA256SUMS
BACKUP_PASSPHRASE=... ./scripts/restore.sh ...        # shifrlangan (*.enc) nusxa uchun (yoki .env da)
./scripts/restore.sh backups/20261119T030000Z         # tasdiq so'raydi; --db-only, --yes
```

`.env` da `BACKUP_PASSPHRASE` berilsa nusxalar shifrlanadi (AES-256). Parolni serverdan tashqarida ham saqlang:
parolsiz tiklab bo'lmaydi.

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
| **OutboxBacklog / OutboxDeadEvents** | `GET /api/v1/admin/outbox/dead` (xato matni bilan). Sabab tuzatilgach: `POST /api/v1/admin/outbox/dead/retry` |
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

Faqat test yoki staging muhitida ishlating: script `.env` dagi JWT siri bilan test tokenlari yasaydi va
2 500 ta test foydalanuvchi yaratadi.
