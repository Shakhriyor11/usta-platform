# Usta chaqirish platformasi — Backend

Mijozlar usta (santexnik, elektrik, ta'mirchi va h.k.) topadigan, buyurtma beradigan va
baholaydigan marketplace backend'i. Modular monolit (DDD), Java 25 + Spring Boot 4.

- Arxitektura, status mashinasi, ER diagramma: [docs/architecture.md](docs/architecture.md)
- Unumdorlik va yuklama testi: [docs/performance.md](docs/performance.md)
- Xavfsizlik auditi: [docs/security.md](docs/security.md)
- Operatsion qo'llanma (deploy, rollback, backup, alertlar): [docs/operations.md](docs/operations.md)
- API hujjati: Swagger UI — `/swagger-ui.html` (OpenAPI JSON — `/v3/api-docs`). Bosh sahifada "Yangiliklar" va
  "Xato kodlari" bo'limlari, yangi/o'zgargan endpoint'lar 🆕 / ♻️ bilan belgilangan

## Stek

Java 25 · Spring Boot 4.1 (MVC, Security, Data JPA, Validation, WebSocket) · Spring Modulith ·
PostgreSQL 17 + PostGIS · Flyway · Redis · MinIO (S3) · MapStruct · springdoc-openapi ·
Micrometer/Prometheus/Grafana · JUnit 5, Testcontainers, ArchUnit · Docker Compose, Nginx, GitHub Actions.

## Tez ishga tushirish (to'liq stek)

Talab: Docker 24+ va Docker Compose v2.

```bash
cp .env.example .env            # JWT_SECRET majburiy: openssl rand -base64 48 natijasini yozing
docker compose up -d            # birinchi marta image build qilinadi (~3–5 daqiqa)
docker compose ps               # hammasi healthy bo'lishini kuting
curl -k https://localhost/api/v1/public/meta
```

Stek `prod` profilida ishlaydi: `JWT_SECRET` bo'sh yoki namunaviy bo'lsa, yoki `OTP_FIXED_CODE` berilgan bo'lsa
api ishga tushmaydi. Lokal sinovda `.env` da `SMS_PROVIDER=log` qoldiring.

| Servis | Manzil |
|---|---|
| API (nginx orqali, self-signed TLS) | https://localhost/api/v1/... |
| Swagger UI (`.env` da `SWAGGER_ENABLED=true` bo'lsa) | https://localhost/swagger-ui.html |
| Grafana | http://127.0.0.1:3000 (admin / admin) |
| Prometheus | http://127.0.0.1:9090 |
| MinIO konsol | http://127.0.0.1:9001 |

Standart parollar faqat lokal uchun. **Production**: barcha
`change-me-*` qiymatlarni almashtiring, `BACKUP_PASSPHRASE` bering, `deploy/nginx/certs/` ga haqiqiy sertifikat
(`server.crt`, `server.key`) qo'ying va `docker compose up -d --build`.

## API qisqacha

| Guruh | Yo'llar | Kim |
|---|---|---|
| Auth | `POST /api/v1/auth/otp/request`, `/otp/verify`, `/refresh`, `/logout` | hamma |
| Me | `GET /api/v1/me`, `POST /api/v1/me/roles` | token bilan |
| Katalog | `GET /api/v1/public/catalog/categories`, `/categories/{id}/services`, `/services?q=`, `/services/{id}`, `/icons`, `/icons/{key}.svg` | hamma |
| Mijoz | `/api/v1/customer/profile` (+`/avatar`), `/api/v1/customer/addresses` | CUSTOMER |
| Usta | `/api/v1/master/profile` (+`/avatar`), `/verification/documents`, `/verification/submit`, `/services/{serviceId}`, `/portfolio` | MASTER |
| Usta kartochkasi | `GET /api/v1/masters/{id}`, `/{id}/services`, `/{id}/portfolio` | token bilan |
| Qidiruv | `GET /api/v1/search/masters?serviceId|categoryId&lat&lng|addressId&radiusKm&minRating&priceMin&priceMax&onlineOnly&sort` | token bilan |
| Mijoz: buyurtmalar | `/api/v1/customer/orders` (+`/{id}/cancel`, `/dispute`, `/attachments`, `/review`) | CUSTOMER |
| Usta: buyurtmalar | `/api/v1/master/offers` (+`/{orderId}/accept`, `/reject`), `/master/orders` (+`/start`, `/complete`, `/cancel`, `/dispute`), `/master/presence/heartbeat` | MASTER |
| Sharhlar | `GET /api/v1/masters/{id}/reviews` | token bilan |
| Bildirishnomalar | `/api/v1/notifications` (+`/unread-count`, `/{id}/read`, `/read-all`), `/api/v1/push-tokens` | token bilan |
| Chat | `/api/v1/orders/{orderId}/chat` (+`/messages`, `/images`, `/read`); real vaqt — STOMP `wss://<host>/ws` | ishtirokchilar |
| Shikoyatlar | `/api/v1/complaints` | token bilan |
| Usta: moliya | `/api/v1/master/finance/summary`, `/commissions`, `/transactions` | MASTER |
| Super admin | `/api/v1/admin/admins` (+`/permissions`, `/{id}`) | SUPER_ADMIN |
| Admin: foydalanuvchilar | `/api/v1/admin/users` (+`/{id}/block`, `/unblock`, `DELETE /{id}`) | `USERS` (+`DELETE`) |
| Admin: ustalar | `/api/v1/admin/masters` (+`/approve`, `/reject`) | `MASTERS` |
| Admin: katalog | `/api/v1/admin/catalog/categories`, `/services` (`DELETE /{id}`, `PUT·DELETE /{id}/image`, `/{id}/icon`) | `CATALOG` (o'chirish: +`DELETE`) |
| Admin: buyurtmalar | `/api/v1/admin/orders` (+`/{id}/resolve`, `/{id}/chat/messages`) | `ORDERS` (chat: yoki `COMPLAINTS`) |
| Admin: moliya | `/api/v1/admin/finance/invoices` (+`/close`), `/commissions`, `/transactions`, `/settlements`, `/reconciliation` | `FINANCE` |
| Admin: shikoyatlar | `/api/v1/admin/complaints` (+`/{id}/take`, `/close`) | `COMPLAINTS` |
| Admin: tizim | `/api/v1/admin/search/reindex`, `/api/v1/admin/outbox/dead` (+`/retry`) | `SYSTEM` |

Asosiy biznes qoidalari: taklif avval online ustalarga boradi; komissiya to'lovi muddati o'tgan usta yangi
buyurtma olmaydi; yakuniy narx musbat bo'lishi shart; usta voz kechsa buyurtma qayta qidiriladi; rejalashtirilgan
buyurtma vaqtidan 1 soat oldingacha qidiriladi. Batafsil — [docs/architecture.md](docs/architecture.md).

To'liq sxema va misollar — Swagger UI. Lokal sinov: `SMS_PROVIDER=log` da OTP kodi loglarda
(`docker compose logs api | grep "SMS stub"`).

### Adminlar

- **Super admin** — `.env` da `SUPER_ADMIN_PHONES=+998...` (eski `ADMIN_PHONES` ham ishlaydi) va shu raqam bilan
  kirish. Barcha huquqlarga ega, adminlarni tayinlaydi va olib tashlaydi. Uni API orqali o'zgartirib bo'lmaydi.
- **Admin** — super admin telefon raqami bo'yicha tayinlaydi va huquqlar beradi: `USERS`, `MASTERS`, `CATALOG`,
  `ORDERS`, `FINANCE`, `COMPLAINTS`, `SYSTEM`, `DELETE` (o'chirish uchun qo'shimcha). Huquq o'zgarsa adminning
  eski tokeni darhol `401 TOKEN_OUTDATED` oladi — refresh qilish kerak.

### Til, rasm va ikonkalar

- `Accept-Language`: `uz` (standart), `uz-Cyrl`, `ru`, `en`. Katalog nomi uch tilda (`uz`, `ru`, `en`) majburiy;
  `uzCyrl` bo'sh bo'lsa lotinchadan o'giriladi.
- Ikonka: tayyor to'plamdan (`GET /public/catalog/icons`, so'rovda `icon: "pipe"`) yoki o'z fayli (kvadrat
  PNG/WEBP, 48–1024 px, 512 KB). Rasm: JPEG/PNG/WEBP, 200–4096 px, 5 MB. SVG yuklab bo'lmaydi.
- Chatda rasm: `POST /orders/{orderId}/chat/images` (multipart `file`, ixtiyoriy `caption`), 10 MB gacha.

## Lokal ishlab chiqish

```bash
docker compose up -d postgres redis minio   # faqat infratuzilma
./gradlew bootRun                           # profil: local, port 8080
```

Swagger: http://localhost:8080/swagger-ui.html · Actuator: http://localhost:8081/actuator/health

JDK 25 o'rnatilmagan bo'lsa Gradle toolchain uni avtomatik yuklab oladi. IntelliJ'da `UstaApplication` ni
oddiy ishga tushirish kifoya (profil — `local`).

Host porti band bo'lsa (masalan, kompyuterda o'zingizning PostgreSQL 5432 da ishlasa): stekni
`POSTGRES_HOST_PORT=5433 docker compose up -d` bilan ko'taring va ilovaga ham shu portni bering — env
`POSTGRES_HOST_PORT=5433` yoki git'ga tushmaydigan `config/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/usta
```

## Testlar

```bash
./gradlew test        # unit + integratsion (Testcontainers: PostGIS, Redis, MinIO) + ArchUnit + Modulith
./gradlew build       # testlar + bootJar
```

Integratsion testlar uchun Docker ishlab turishi kerak. Modul diagrammalari
`build/spring-modulith-docs/` ga yoziladi.

## Repo tuzilmasi

```
├── src/main/java/uz/usta/platform/
│   ├── identity/  profile/  catalog/  search/  order/  review/
│   ├── payment/  chat/  notification/  complaint/  storage/  admin/
│   └── shared/            # error, i18n, security, outbox, persistence, web, util
├── src/main/resources/
│   ├── db/migration/      # Flyway (V1__baseline, V2__identity, ...)
│   ├── catalog/icons/     # tayyor ikonkalar to'plami (Lucide, ISC)
│   └── i18n/              # messages(_uz_Cyrl|_ru|_en).properties — standart: uz
├── src/test/java/...      # unit, *IT (Testcontainers), architecture/
├── deploy/                # nginx, prometheus, grafana konfiguratsiyalari
├── scripts/               # backup.sh, restore.sh
├── docs/                  # arxitektura, ER diagramma
├── .github/workflows/     # CI/CD
├── docker-compose.yml  Dockerfile  .env.example
```

## Konfiguratsiya

Barcha maxfiy qiymatlar environment o'zgaruvchilari orqali beriladi (`.env.example` ga qarang).
Asosiy o'zgaruvchilar: `JWT_SECRET` (prod'da majburiy), `SUPER_ADMIN_PHONES`, `SMS_PROVIDER`, `ESKIZ_*`, `PUSH_PROVIDER`, `S3_PUBLIC_ENDPOINT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PASSWORD`,
`S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `CORS_ALLOWED_ORIGINS`, `SWAGGER_ENABLED`,
`APP_LOG_LEVEL`. Biznes parametrlari `application.yml` dagi `app.*` bo'limida.

Loglar: `local` profilida oddiy matn, `prod` profilida JSON (ECS). Har bir so'rovga
`X-Request-Id` beriladi va loglarda `traceId` sifatida chiqadi.

## Monitoring va alertlar

Grafana (http://127.0.0.1:3000) → "Usta overview" dashboard'i. Unda HTTP RPS, p95/p99, 5xx, JVM, DB pool,
outbox, buyurtmalar oqimi, reconciliation va faol alertlar bor. 10 ta alert qoidasi `deploy/prometheus/alerts.yml`
da va Grafana → Alerting bo'limida ko'rinadi. Har alertga qanday javob berish kerakligi
[docs/operations.md](docs/operations.md) da.

## Backup va restore

```bash
./scripts/backup.sh                                   # backups/<timestamp>/ (postgres.dump, minio.tar.gz, SHA256SUMS)
./scripts/restore.sh backups/20261118T030000Z         # tasdiq so'raydi; --db-only, --yes
```

`.env` da `BACKUP_PASSPHRASE` berilsa fayllar shifrlanadi (`*.enc`, AES-256) — production'da majburiy deb hisoblang.

Saqlash muddati: `BACKUP_RETENTION_DAYS` (standart 14 kun). Cron misoli skript boshida.

## CI/CD

`.github/workflows/ci.yml`:
1. **build** — har PR/push: `./gradlew build` (barcha testlar) + compose fayl validatsiyasi
2. **monitoring** — alert qoidalari: `promtool check` + unit testlar
3. **security** — Trivy: image va repo (tuzatilishi mumkin bo'lgan HIGH/CRITICAL topilsa pipeline to'xtaydi)
4. **image** — `main`/`master`/teg push: Docker image → `ghcr.io/<repo>/api`
5. **deploy** — `v*` teg: SSH → `scripts/deploy.sh` (backup → pull → up → health/smoke → muvaffaqiyatsiz bo'lsa rollback)

Dependabot har hafta Gradle, Docker va Actions yangilanishlari uchun PR ochadi.
   (secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH`)

## Yo'l xaritasi

| Bosqich | Mazmun | Holat |
|---|---|---|
| P1 Foundation | repo, Gradle, Compose, Flyway, CI, xatolik formati, logging, outbox | ✅ |
| P2 Identity & Catalog | OTP + JWT, rollar, profillar, katalog, verifikatsiya, admin API | ✅ |
| P3 Core Business | geo qidiruv, buyurtmalar, takliflar, reyting, push | ✅ |
| P4 Commercial | to'lov, komissiya, ledger, chat, shikoyat/nizolar | ✅ |
| P5 Production | yuklama testi, xavfsizlik auditi, alerting | ✅ |
