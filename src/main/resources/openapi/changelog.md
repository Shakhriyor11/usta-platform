### 1. Ilovada albatta o'zgartirish kerak

| Mavzu | Nima qilish kerak |
|---|---|
| `401 TOKEN_OUTDATED` | Foydalanuvchini chiqarib yubormang: `POST /auth/refresh` qiling, so'rovni takrorlang. Admin huquqi o'zgarganda keladi |
| `403 USER_BLOCKED` | Har qanday so'rovda kelishi mumkin (bloklangan yoki o'chirilgan hisob) — sessiyani tozalab, kirish ekraniga |
| Admin panel menyusi | `user.permissions` bo'yicha ko'rsating: `USERS`, `MASTERS`, `CATALOG`, `ORDERS`, `FINANCE`, `COMPLAINTS`, `SYSTEM`, `DELETE`. Adminlar bo'limi — faqat `SUPER_ADMIN` |
| Yangi enum qiymatlari | `roles`: `SUPER_ADMIN`; foydalanuvchi `status`: `DELETED`; bildirishnoma `type`: `ORDER_REOPENED` |
| Katalog formasi (admin) | `name.uz`, `name.ru`, `name.en` majburiy (kamida 2 belgi), `uzCyrl` ixtiyoriy; `icon` — faqat to'plamdagi kalit |
| Ism maydonlari | `fullName` kamida 2 belgi (mijoz va usta) |
| Matn maydonlari | Boshqaruv va ko'rinmas belgilar (`U+202E`, `U+200B` va h.k.) `400 VALIDATION_FAILED`; yuborishdan oldin tozalang |
| Yakuniy narx | Kamida 1 so'm |
| Usta voz kechishi | Buyurtma `CANCELLED` emas, yana `SEARCHING` bo'ladi |
| Chat | Har usta bilan alohida chat; rasmli xabarda `content` bo'sh, `imageUrl` to'ldirilgan bo'lishi mumkin |
| WebSocket | `SEND` faqat `/app/**` ga; obuna faqat o'z buyurtmalari chatiga |
| Xato sarlavhasi | `title` endi `{0}` o'rinbosarlarisiz; matnga emas, `code` ga tayaning |

### 2. Ikonka va rasmlar

- `iconUrl` — ko'rsatiladigan ikonka. `/api/v1/public/catalog/icons/…svg` ko'rinishidagi **nisbiy** yo'l (API manzilini
  qo'shing; bir rangli SVG, rangini ilova beradi) yoki admin yuklagan faylning **to'liq** havolasi. `null` bo'lishi
  mumkin — standart ikonka ko'rsating.
- `imageUrl`, `avatarUrl`, chat `imageUrl` — 15 daqiqalik havola. Bazaga saqlamang; muddati o'tsa ma'lumotni qayta oling.
- Yuklash: `multipart/form-data`, fayl maydoni `file`. Turi fayl mazmunidan aniqlanadi, SVG qabul qilinmaydi.
  Rasmdagi EXIF (joylashuv) o'chiriladi.

| Maqsad | Turlar | Hajm | O'lcham (px) |
|---|---|---|---|
| Avatar | JPEG, PNG, WEBP | 5 MB | ≤ 12000 |
| Usta hujjati | JPEG, PNG, WEBP, PDF | 10 MB | ≤ 12000 |
| Portfolio, chat | JPEG, PNG, WEBP | 10 MB | ≤ 12000 |
| Buyurtma fayli | JPEG, PNG, WEBP, MP4 | 50 MB (rasm 20 MB) | ≤ 12000 |
| Kategoriya/xizmat rasmi | JPEG, PNG, WEBP | 5 MB | 200–4096 |
| Ikonka | PNG, WEBP (kvadrat) | 512 KB | 48–1024 |

Yuklash xatolari: `FILE_EMPTY`, `FILE_TOO_LARGE` (413), `FILE_TYPE_NOT_ALLOWED` (415), `IMAGE_INVALID`,
`IMAGE_DIMENSIONS`, `IMAGE_NOT_SQUARE`.

### 3. Cheklovlar (`429`, `Retry-After` sarlavhasi va `retryAfterSeconds`)

| Amal | Cheklov |
|---|---|
| SMS kod | 60 s oraliq; raqamga soatiga 5, sutkasiga 10 |
| Noto'g'ri kod | raqamga sutkasiga 15 |
| Buyurtma | soatiga 20 |
| Shikoyat | sutkasiga 10 |
| Fayl yuklash | soatiga 60 (admin panel bundan mustasno) |
| Chat xabari (rasm bilan) | daqiqasiga 30 |

### 4. Boshqa

- Ro'yxatlarda noto'g'ri `?sort=` maydoni — `400 BAD_REQUEST`.
- O'chirilgan foydalanuvchi suhbatdosh bo'lsa, ismi "Oʻchirilgan foydalanuvchi" yoki `null` keladi, telefoni `null`.
- O'chirilgan xizmat eski buyurtmalarda nomi bilan qoladi.
