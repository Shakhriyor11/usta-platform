# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Usta chaqirish platformasi backend'i: Java 25, Spring Boot 4.1, Spring Modulith (modular monolit), PostgreSQL+PostGIS, Redis, MinIO. Loyiha tili o'zbekcha — kod izohlari, hujjatlar (`docs/`), testlar va foydalanuvchi bilan muloqot o'zbek tilida. Umumiy ma'lumot va API jadvali — `README.md`, chuqur arxitektura (status mashinasi, ER, Redis kalitlari) — `docs/architecture.md`.

## Buyruqlar

```bash
docker compose up -d postgres redis minio   # lokal infratuzilma (host'da 5432 band bo'lsa: POSTGRES_HOST_PORT=5433 ...)
./gradlew bootRun                           # profil `local`, API :8080, actuator :8081
./gradlew test                              # barcha testlar (Docker kerak — Testcontainers)
./gradlew build                             # testlar + bootJar (CI shuni ishlatadi)
./gradlew test --tests 'uz.usta.platform.order.OrderFlowIT'              # bitta test sinfi
./gradlew test --tests 'uz.usta.platform.order.OrderFlowIT.methodName'   # bitta metod
docker compose up -d                        # to'liq stek (nginx TLS orqali https://localhost)
PROFILE=load ./loadtest/run.sh              # yuklama testi (faqat test muhitida), keyin ./loadtest/run.sh cleanup
```

Alohida linter yo'q; format `.editorconfig` bilan (4 bo'sh joy, Java; 2 — yml/json). Alert qoidalarini tekshirish: `promtool check rules` / `promtool test rules` (`deploy/prometheus/`, CI'dagi `monitoring` job).

Host'dagi Postgres porti boshqacha bo'lsa, ilovaga `POSTGRES_HOST_PORT` env yoki git'ga tushmaydigan `config/application-local.yml` (`spring.datasource.url`) bering.

## Arxitektura

Modullar `src/main/java/uz/usta/platform/<modul>/`: `identity, profile, catalog, search, order, review, payment, chat, notification, complaint, storage, admin` va `shared` (OPEN shared kernel — domen logikasi bo'lmasligi kerak).

**Modul qoidasi** (testlar majburlaydi):
- Modul paketining **ildizi** — ochiq API (interfeys, DTO, `*Events` yozuvlari, masalan `OrderApi`, `OrderEvents`, `CommissionLedger`). Boshqa modul faqat shularga bog'lanadi.
- `internal/{domain,application,persistence,web}` — yopiq. `domain` — entity/status mashinasi, `application` — `@Transactional` servislar, `persistence` — Spring Data, `web` — controller + DTO + MapStruct mapper.
- `ModularityTest` (Spring Modulith: internal'ga murojaat va sikl yo'q) va `ArchitectureRulesTest` (ArchUnit: field injection yo'q, `java.util.logging`/`System.out` yo'q, `@RestController` faqat `..internal.web..`/`..shared.web..`da, controller repository'ga to'g'ridan-to'g'ri murojaat qilmaydi, `@Entity` faqat `..internal.domain..`da va API'dan qaytmaydi). Yangi modul qo'shsangiz `package-info.java` da `@ApplicationModule` kerak.

**Modullararo aloqa — Transactional Outbox.** Biznes o'zgarish va `outbox_events` qatori bitta tranzaksiyada yoziladi (`shared/outbox`: `OutboxPublisher` → `OutboxRelay` (ShedLock, `FOR UPDATE SKIP LOCKED`) → `OutboxDispatcher`, hozir in-process Spring event). Event iste'molchilari **idempotent** bo'lishi shart (event `id` bo'yicha). Tinglash usuli — `OutboxMessage` ustida `@EventListener(condition = "#m.eventType == '<ochiq-event-nomi>'")` (event nomlari modul ildizidagi `*Events` sinflarida, masalan `OrderEvents.STATUS_CHANGED`); namuna — `notification/.../DomainEventsListener`. Yangi event turi qo'shsangiz, nomini shu modulning `*Events` sinfiga qo'ying.

**Buyurtma** (`order`): status mashinasi `CREATED→SEARCHING→ACCEPTED→IN_PROGRESS→COMPLETED→COMMISSION→REVIEWED` (+ `CANCELLED/EXPIRED/DISPUTED`); har o'tishda ruxsat jadvali tekshiriladi, `order_status_history` yoziladi, outbox event ketadi. Bir vaqtda ikki usta qabul qilmasligi uchun `orders` qatori `SELECT … FOR UPDATE` + `@Version`. `REJECTED` — buyurtma emas, taklif (`order_master_offers`) holati.

**Provayderlar `@ConditionalOnProperty` bilan almashadi** (`app.sms.provider`: `log|eskiz`; `app.push.provider`: `log|fcm`). Lokal/test'da `log` stub. `OTP_FIXED_CODE` (doimiy OTP, lokal uchun) `SMS_PROVIDER=log` bo'lmasa `OtpService` konstruktorida `IllegalStateException` bilan ilovani ishga tushirmaydi. Prod profilida `shared/ops/ProductionReadinessCheck` xavfli sozlamalarni (placeholder JWT siri, stub SMS/push, ochiq Swagger…) ilovani to'xtatmasdan WARN log va `app.config.warnings` metrikasiga yozadi.

**Boshqa kelishuvlar:**
- Hamma ID — UUID v7 (`shared/util/Uuids`), pul — `NUMERIC(14,2)` UZS, vaqt UTC (komissiya haftasi — Asia/Tashkent, dushanba).
- Sxema faqat Flyway orqali (`src/main/resources/db/migration/V<n>__*.sql`); `ddl-auto: validate`. Mavjud migratsiyani tahrirlamang — yangisini qo'shing.
- Xatolar: `BusinessException` + `ErrorCode` → `GlobalExceptionHandler`; matnlar i18n (`src/main/resources/i18n/messages(_ru|_en).properties`, standart uz) — yangi xabar kaliti uch tilga ham qo'shiladi.
- Auth: telefon+OTP (Redis'da HMAC hash), JWT access (15 daq) + aylanuvchi refresh; ADMIN roli `ADMIN_PHONES` raqamlariga beriladi. Rate limit — Redis Lua (`shared/ratelimit`).
- MapStruct `unmappedTargetPolicy=ERROR` — mapper'da barcha maydonlar aniq xaritalanishi kerak.
- Virtual thread yoqilgan; ShedLock bilan klasterda cron'lar bitta instansiyada ishlaydi.

## Testlar

- `*IT` — integratsion: `@IntegrationTest` meta-annotatsiyasi (`support/`) to'liq kontekst + Testcontainers (PostGIS `postgis/postgis:17-3.6`, Redis, MinIO) + MockMvc, profil `test` (`src/test/resources/application-test.yml`: scheduling/warmup o'chiq, OTP limitlari yuqori, actuator porti `0`). Cron'lar o'chiq bo'lgani uchun testlar outbox relay'ni o'zi chaqiradi. Yordamchilar: `ApiClient`, `Fixtures`, `Seeds`, `RecordingSmsSender`.
- Unit testlar (`*Test`) Docker talab qilmaydi, lekin `ModularityTest` Modulith hujjatini `build/spring-modulith-docs/` ga yozadi.
- Spring Boot 4: test paketlari yangi (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`), eski Boot 3 import'larini ishlatmang.

## Deploy / operatsiya

`docker-compose.yml` (api, postgres, redis, minio, nginx, prometheus, grafana), `.env.example` → `.env` (`change-me-*` almashtiriladi). Maxfiy fayllar (masalan Firebase service account) `./secrets/` da, konteynerda `/run/secrets/` — git'ga tushmaydi. CI: `.github/workflows/ci.yml` (build+test, promtool, Trivy, image, `v*` tegda SSH deploy `scripts/deploy.sh`). Backup/restore — `scripts/backup.sh`, `scripts/restore.sh`; alertlarga javob — `docs/operations.md`.
