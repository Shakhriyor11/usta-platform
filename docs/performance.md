# Unumdorlik va yuklama testi (P5, 2026-11-19)

## Maqsad (tasdiqlangan taxminlar)

- **1 000 bir vaqtdagi foydalanuvchi.** Har biri ~7 s da bitta so'rov yuboradi, bu **~140 RPS** o'qish. Bunga
  ustalarning heartbeat signali (500 usta / 30 s ≈ 17 RPS) qo'shiladi.
- **Kuniga 5 000 buyurtma.** Eng band soat taxminan 750 buyurtma, ya'ni ~0.2/s. Test **1 buyurtma/s** bilan,
  5 baravar zaxira bilan o'tkazildi, har buyurtma to'liq sikldan o'tadi: yaratish → taklif → qabul
  (row lock) → 2 ta chat xabari → boshlash → yakunlash (komissiya, ledger) → baho (reyting).
- **Server: 4 vCPU / 8 GB, bitta mashina.** Compose resurs limitlari shunga moslangan: API 2 CPU / 2 GB,
  PostgreSQL 1.5 CPU / 2 GB, qolganlari 0.25 CPU dan. Hammasi env orqali sozlanadi: `API_CPUS`, `PG_CPUS`, ...

SLO (k6 threshold'lari): xatolar < 1%, o'qish p95 < 300 ms va p99 < 800 ms, yozish p95 < 500 ms, to'liq
buyurtma oqimi p95 < 3 s.

## Usul

- `loadtest/run.sh`: seed (2 000 mijoz, 500 tasdiqlangan usta Toshkent bo'ylab, 1 500 narx) → qidiruv indeksini
  qurish → k6 docker tarmog'ida, API'ga to'g'ridan-to'g'ri.
- Nginx chetlab o'tiladi: u IP bo'yicha cheklaydi, k6 esa bitta IP'dan minglab foydalanuvchini taqlid qiladi.
- Tokenlar k6 ichida imzolanadi (OTP/SMS chetlab o'tiladi).
- Trafik aralashmasi:
  - qidiruv: 45%, nuqtalarning 60% i 150 ta "mahalla" markazidan, 40% i tasodifiy;
  - usta kartochkasi, xizmatlari va sharhlari: 30%;
  - katalog: 10%;
  - bildirishnomalar soni va buyurtmalarim: 15%.
- Monitoring: `docker stats` (CPU limitga nisbatan), Prometheus (HTTP, Hikari, GC), `pg_stat_statements`.
- Qurilma: 12 yadroli noutbuk. Stek compose limitlari bilan cheklangan, k6 limitdan tashqarida ishladi.

## Natijalar

### Maqsadli yuklama (`PROFILE=load`, 5 daq, qizigan JVM) — ✅ barcha SLO bajarildi

| Ko'rsatkich | Natija | SLO |
|---|---|---|
| Throughput | **164 RPS** (49 230 so'rov) | — |
| Xatolar | **0.00%** | < 1% |
| O'qish p95 / p99 | **14 ms / 20 ms** | 300 / 800 ms |
| Yozish p95 / p99 | **9 ms / 17 ms** | 500 ms |
| To'liq buyurtma oqimi p95 | **59 ms** (9 ta so'rov) | 3 s |
| API CPU (2 CPU limitdan) | ≤ 69% | — |
| PostgreSQL CPU | ≤ 8% | — |
| Hikari navbati | 0 | — |
| GC pauza (max) | 24 ms | — |

### Chegara (`PROFILE=stress`, 100 → 1 200 RPS)

| Bosqich | To'yinish nuqtasi | Chegara |
|---|---|---|
| Boshlang'ich kod | **~300 RPS** | PostgreSQL CPU (1 CPU) — qidiruv so'rovi |
| Qidiruv SQL optimallashtirilgach | **~400 RPS** | PostgreSQL CPU (1 CPU) |
| + PostgreSQL 1.5 CPU | **~500 RPS** | PostgreSQL CPU (1.5 CPU) |

To'yinishdan keyin so'rovlar DB pool'ida navbatda kutadi va 5 s `connection-timeout` dan keyin xato bilan
tugaydi. Tizim qulamaydi: yuklama tushgach darhol tiklanadi. **Zaxira: maqsadli yuklamadan ~3 baravar.**

### Topilmalar va tuzatishlar

**1. Qidiruv so'rovi DB vaqtining ~99% ini egallardi.** Har qidiruv 2 ta so'rov edi (natijalar + `count(*)`),
masofa spheroid modelda va har qator uchun 2–3 marta hisoblanardi. Tuzatish (`SearchIndexRepository.search`):
- bitta so'rov va `count(*) OVER ()`;
- sferik model (`ST_DWithin(..., false)`, xato < 0.5%, ~3 baravar arzon);
- masofa har usta uchun bir marta hisoblanadi.

Natijada `EXPLAIN ANALYZE` **7.4 ms → 1.1 ms** ko'rsatdi va ikkinchi so'rov yo'qoldi; to'yinish nuqtasi
300 → 400 RPS ga ko'tarildi.

**2. Sovuq ishga tushish.** Deploy'dan keyingi birinchi daqiqada so'rovlarning dumi uzun edi (p99 ~0.5 s,
maksimum ~2.5 s, hamma endpointlarda bir vaqtda). Sabab — JIT kompilyatsiyasi; GC, DB pool va so'rovlarning
o'zi emas. Qizigan JVM'da p99 = 20 ms. Tuzatish: `WarmupRunner` readiness UP bo'lishidan oldin ~1 400 ta lokal
so'rov yuboradi. A/B o'lchovi: faqat API qayta yaratildi, PostgreSQL qizigan holda qoldi; restartdan darhol
keyin 2 daqiqa `load` yuklamasi:

| Restartdan keyingi 2 daqiqa | warm-up'siz | warm-up bilan |
|---|---|---|
| O'qish p95 / p99 / max | 1.30 s / 2.93 s / 5.9 s | **445 ms / 940 ms / 2.8 s** |
| Yozish p99 | 1.46 s | **423 ms** |
| Buyurtma oqimi p95 | 5.2 s | **2.4 s** |
| Xatolar | 0.08% | **0%** |

Warm-up dumni ~3 baravar qisqartiradi, lekin restartdan keyingi birinchi 1–2 daqiqada o'qish p95 hali ham SLO'dan
(300 ms) yuqori. Sabab: 2 CPU limiti ichida JIT kompilyatsiyasi trafik bilan raqobatlashadi. Production uchun
tavsiya:
- **rolling deploy**: 2 ta API instansiyasi, yangisi qizigach eskisi to'xtatiladi;
- keyinchalik JDK AOT cache (`-XX:AOTCache`, JEP 515), JIT profilini oldindan yig'ish.

**3. Gistogramma yo'q edi.** Grafana'dagi "p95" paneli bo'sh qolardi, chunki Spring standart holatda bucket
chiqarmaydi. 8 ta SLO bucket qo'shildi (25 ms … 5 s): `ApiHighLatency` alerti va p95/p99 panellari ishlaydi.

## Masshtablash yo'l xaritasi

1. **Vertikal:** server 8 vCPU va `PG_CPUS=3`. Qidiruv CPU'ga bog'liq, shuning uchun taxminan chiziqli o'sadi.
2. **Qidiruv uchun read-replica.** Search moduli faqat o'z read-modelini o'qiydi, uni replica'ga yo'naltirish oson.
3. **Indeks tuzilmasi:** 10 000+ usta bo'lganda `search_master_services` ga `location` ustunini qo'shish
   (denormalizatsiya) va `(service_id) + GIST(location)` indeksi (btree_gist). Hozir xizmatlar jadvali to'liq
   aylanib chiqiladi; 500 usta uchun bu arzon.
4. **Gorizontal API:** bir nechta instansiya — tayyor (chat: Redis pub/sub, joblar: ShedLock, sessiyasiz JWT).
5. **Kesh:** qidiruv natijalari 60 s keshlanadi (koordinata ~11 m gacha yaxlitlanadi). Real foydalanuvchilar
   joylashuvi tarqoq, shuning uchun foydasi cheklangan. Aniqlikni ~100 m gacha kamaytirish hit-rate'ni oshiradi.

## Takrorlash

```bash
PROFILE=load ./loadtest/run.sh       # natija: loadtest/results/<vaqt>-load.{txt,json}
PROFILE=stress ./loadtest/run.sh
./loadtest/run.sh cleanup
```
