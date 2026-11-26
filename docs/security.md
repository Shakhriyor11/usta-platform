# Xavfsizlik auditi (P5, 2026-11-19)

Qamrov: autentifikatsiya va avtorizatsiya, kirish ma'lumotlari, fayllar, tarmoq chegarasi (nginx), konteynerlar,
kutubxonalardagi zaifliklar, sirlar va loglar. Usullar: kodni ko'rib chiqish, integratsion testlar,
Trivy (image + repo), real stekda qo'lda tekshiruv.

## Xulosa

| # | Topilma | Daraja | Holat |
|---|---|---|---|
| 1 | Bloklangan foydalanuvchining access tokeni 15 daqiqagacha ishlashda davom etardi (buyurtma yaratish, chat va h.k.) | **Yuqori** | ✅ Tuzatildi |
| 2 | Base image'da Go stdlib CVE (`/usr/bin/pebble`, ilova ishlatmaydi) | Yuqori (Trivy) | ✅ Olib tashlandi |
| 3 | Nginx 429 javobi HTML edi (API esa `problem+json`) | Past | ✅ Tuzatildi |
| 4 | Auth (OTP) yo'llari umumiy limit ostida edi | O'rta | ✅ Alohida zona `NGINX_AUTH_RATE` |
| 5 | IP bo'yicha limit va CGNAT: mobil operatorlarda bitta IP ortida ko'p foydalanuvchi | O'rta (mavjudlik) | ✅ Limitlar env orqali sozlanadi |
| 6 | Production'da xavfli standart sozlamalar sezilmay qolishi mumkin edi (JWT siri, SMS/push stub, Swagger) | O'rta | ✅ Startup tekshiruvi + metrika + alert |
| 7 | API konteyneri to'liq imtiyozlar bilan ishlardi | Past | ✅ `cap_drop: ALL`, read-only FS, `no-new-privileges` |
| 8 | CD: image tegi mos kelmasligi (`1.2.3` va `v1.2.3`), health tekshiruvi va rollback yo'q edi | O'rta (operatsion) | ✅ `scripts/deploy.sh` |
| 9 | Ilova PostgreSQL superuser bilan ulanadi | Past | ⚠️ Tavsiya (pastda) |

Trivy natijasi (tuzatishlardan keyin): **0 ta HIGH/CRITICAL** — image'dagi barcha JAR'lar va OS paketlari toza
(`--ignore-unfixed`). Repo: sirlar va noto'g'ri sozlamalar yo'q (`deploy/nginx/certs/server.key` — lokal
self-signed, `.gitignore` da). CI har PR'da qayta tekshiradi (`security` job) va dependabot har hafta
yangilanish PR'larini ochadi.

## 1. Bloklangan foydalanuvchi (tuzatildi)

**Muammo.** Access token stateless JWT (15 daq). Bloklashda faqat refresh tokenlar bekor qilinardi. Blok
holati faqat `/me` da tekshirilardi, boshqa endpointlar tokenga ishonardi.

**Tuzatish.** Bloklash commit bo'lgandan keyin Redis'ga `revoked:user:{id}` kaliti yoziladi, TTL = access token
TTL + 1 daq. Uni ikki joy tekshiradi:
- `RevokedUserFilter`: har autentifikatsiyalangan REST so'rovda, 403 `USER_BLOCKED` qaytaradi;
- STOMP CONNECT.

Blokdan chiqarilganda kalit o'chiriladi. Redis ishlamasa tekshiruv o'tkazib yuboriladi (fail-open), chunki tokenlar
baribir qisqa muddatli. Test: `AuthFlowIT.adminBlocksAndUnblocksUser`.

Blokdan oldin ochilgan WebSocket sessiyalari ham uziladi (outbox event → Redis `ws:disconnect`), har `SEND` va
`SUBSCRIBE` kadrida blok qayta tekshiriladi. Ustaning profili event orqali nofaol bo'ladi.

## Ikkinchi audit (2026-11-26)

| Topilma | Daraja | Tuzatish |
|---|---|---|
| STOMP `SEND` manzili tekshirilmasdi: istalgan foydalanuvchi chat mavzusiga soxta xabar yubora olardi | **Kritik** | Kadrlar oq ro'yxati, `SEND` faqat `/app/**`, yetkazishda ishtirokchilik tekshiruvi |
| Parallel to'lov so'rovlari bir qarzni bir necha marta ledger'ga yozardi | **Kritik** | Usta hisobi bo'yicha advisory lock |
| To'langan komissiya nizodan keyin qayta talab qilinardi, ortiqcha to'lov chegirilmasdi | **Kritik** | Narx o'zgarmasa saqlanadi; kredit keyingi qarzdan chegiriladi; nizodagi komissiya `on_hold` |
| OTP: raqam bo'yicha kunlik limit yo'q edi | Yuqori | Sutkalik SMS va noto'g'ri urinish limiti |
| Rad etgan usta buyurtmani keyin ham ko'rardi | O'rta | Faqat ochiq taklif ko'rish huquqini beradi |
| Tasdiqlangan usta ism/rasmini almashtira olardi | O'rta | Verifikatsiyaga yuborilgach qulflanadi |
| Prod namunaviy JWT siri / doimiy OTP bilan ishga tushardi; Swagger standart yoqiq edi | O'rta | Ishga tushmaydi; Swagger standart o'chiq |
| Yaratish/yuklashda limit yo'q edi | O'rta | `order-create`, `complaint-create`, `upload` limitlari |
| `ADMIN_PHONES` dan olingan raqam admin bo'lib qolardi; logout push tokenni o'chirmasdi | Past | Rol sinxronlanadi; `pushToken` o'chiriladi |
| Zaxira nusxalar shifrlanmasdi | Past | `BACKUP_PASSPHRASE` (AES-256), `umask 077` |

Qoldiq xavf: begona odam raqamga noto'g'ri kodlar yuborib, egasining kirishini bir sutkagacha bloklay oladi (SMS
OTP'ning tabiiy cheklovi). Kerak bo'lsa: ADMIN uchun ikkinchi omil, kod uzunligini 6 ga oshirish.

## Uchinchi audit (2026-11-26): admin huquqlari, o'chirish, rasmlar

Usullar: yangi kodni ko'rib chiqish, barcha admin endpoint'lari bo'yicha huquq matritsasi testi
(`AdminAccessIT` ro'yxatni ilovaning o'zidan oladi), rasm parserini tasodifiy buzilgan fayllar bilan sinash,
Trivy 0.67.2 (image va repo: **0 ta HIGH/CRITICAL**, sir topilmadi).

| Topilma / xavf | Daraja | Chora |
|---|---|---|
| Har admin hamma narsani qila olardi | **Yuqori** | Super admin + 8 ta huquq; o'chirishga alohida `DELETE`; tasniflanmagan admin yo'li faqat super adminga |
| Admin boshqa adminni boshqara olishi | Yuqori | Faqat super admin; super adminni API orqali o'zgartirib bo'lmaydi; o'zini o'chira olmaydi |
| Huquqi olingan admin tokeni 15 daqiqa ishlashi | O'rta | `authz:ver` — eski token darhol `TOKEN_OUTDATED` |
| Eski `ADMIN` yozuvlari (ro'yxatdan olingan sobiq adminlar) | O'rta | V16 ularga huquq bermaydi |
| Rasmlardagi GPS va qurilma ma'lumoti boshqa foydalanuvchiga yetib borardi | O'rta | `ImageSanitizer`: EXIF/XMP/matn va yopishtirilgan mazmun o'chiriladi |
| Buzilgan yoki ulkan rasm (xotira, "dekompressiya bombasi") | O'rta | Tuzilma va o'lcham tekshiruvi, 20 MB, bir vaqtda 8 ta |
| SVG orqali skript | — | SVG qabul qilinmaydi; to'plam SVG'lari tozalangan, `CSP default-src 'none'; sandbox` |
| Matnlarda ko'rinmas va yo'nalish belgilari (soxtalashtirish) | Past | `@PlainText` |
| Foydalanuvchi "tizim sababi"ni yozib, tizim xabaridek ko'rsatishi | Past | Tizim kodlari faqat muallifsiz yozuvda tarjima qilinadi |
| `config/`, `secrets/`, `backups/` Docker build kontekstiga tushardi | Past | `.dockerignore` |
| Shaxsiy ma'lumotni o'chirib bo'lmasdi | O'rta | Foydalanuvchini anonimlashtirish (architecture.md §9) |

Ochiq qolgan masalalar (biznes qarori kerak):
- Admin o'zi ham usta bo'lsa, o'z verifikatsiyasini tasdiqlashi yoki (`FINANCE` bilan) o'z to'lovini qayd etishi
  mumkin. Barcha amallar `audit_logs` da. Kerak bo'lsa "o'ziga nisbatan amal taqiqlanadi" qoidasini qo'shish.
- Super admin uchun ikkinchi omil yo'q: SIM almashtirilsa, butun tizim ochiladi.
- Huquq/blok tekshiruvi Redis ishlamasa o'tkazib yuboriladi (access token 15 daqiqalik).
- Admin o'chirilgan foydalanuvchining sharh va shikoyat matnlari saqlanadi (ichida ism bo'lishi mumkin).

## Tekshirilgan va muammo topilmagan sohalar

| Soha | Natija |
|---|---|
| OTP | 5 xona, HMAC hash saqlanadi, 5 urinish, 60 s cooldown, soatiga 5 va sutkasiga 10 SMS/raqam, 20/IP, verify 60/IP, sutkasiga 15 noto'g'ri urinish/raqam. Tanlash ehtimoli sutkasiga ≤ 0.015% |
| JWT | HS256, ≥ 32 bayt sir (prod'da namunaviy qiymat bilan ishga tushmaydi), `iss` va `typ=access` tekshiriladi. Refresh token: 256-bit, faqat SHA-256 hash, rotatsiya, qayta ishlatilsa butun family bekor qilinadi |
| Avtorizatsiya | Yo'l bo'yicha rollar + har amalda egalik tekshiruvi. Begona buyurtma, manzil, chat yoki shikoyat — 404 (mavjudligi oshkor qilinmaydi). Integratsion testlar bilan qoplangan |
| Kirish ma'lumotlari | Bean Validation (DTO), JPA va nomlangan parametrlar. Qidiruv SQL'idagi dinamik qismlar faqat konstantalardan yig'iladi (SQL injection yo'q) |
| Fayllar | Turi magic bytes bo'yicha aniqlanadi (SVG/HTML yo'q), rasmlar tozalanadi, hajm cheklangan, bucket private, presigned URL 15 daq, nginx orqali faqat GET/HEAD, `nosniff` |
| Xatolar | Stack trace va ichki xabarlar javobda yo'q; `traceId` bor |
| Loglar | Telefonlar maskalanadi (`+99890***4567`). OTP matni faqat `SMS_PROVIDER=log` da logga yoziladi va prod'da ogohlantirish chiqadi. `X-Request-Id` regex bilan tekshiriladi (log injection yo'q) |
| CORS | Ro'yxatdagi domenlar, `allowCredentials=false`. WebSocket origin'i ham shu ro'yxatdan |
| Actuator | Alohida ichki port (8081), nginx `/actuator` uchun 404 qaytaradi |
| Ledger | `transactions` o'zgarmas (DB trigger), ishoralar `CHECK` bilan majburlangan |
| Chat XSS | API matnni o'zgartirmasdan qaytaradi. **Mijoz ilovalari uni HTML sifatida emas, matn sifatida ko'rsatishi shart** |

## Tavsiyalar (production'ga chiqishdan oldin)

1. **`.env`**: tasodifiy `JWT_SECRET` (`openssl rand -base64 48`), kuchli parollar, `SMS_PROVIDER=eskiz`,
   `SWAGGER_ENABLED=false`, haqiqiy `CORS_ALLOWED_ORIGINS` va `S3_PUBLIC_ENDPOINT`. Ilova ishga tushganda shularni
   tekshiradi: `app.config.warnings` metrikasi 0 bo'lishi kerak, aks holda `ProductionConfigWarnings` alerti yonadi.
2. **DB foydalanuvchisi**: superuser `POSTGRES_USER` o'rniga ilova uchun alohida, superuser bo'lmagan egasi
   (`CREATE ROLE usta_app LOGIN PASSWORD ...; ALTER DATABASE usta OWNER TO usta_app;`). Backup uchun alohida
   faqat o'qiydigan rol.
3. **TLS**: haqiqiy sertifikat (Let's Encrypt) — `deploy/nginx/certs/`. HSTS allaqachon yoqilgan.
4. **CGNAT**: production loglarida edge 429 lar (`"detail":"Edge rate limit"`) ko'paysa, `NGINX_API_RATE` ni
   oshiring. Asosiy himoya ilova ichidagi limitlar: OTP (raqam va IP bo'yicha) va chat (foydalanuvchi bo'yicha).
5. **Sirlar**: `.env` faqat serverda, 600 huquq bilan. CI'da faqat GitHub Secrets.
6. Muntazam: dependabot PR'larini haftalik ko'rib chiqing. Trivy CI'da yangi CVE chiqsa pipeline'ni to'xtatadi.
