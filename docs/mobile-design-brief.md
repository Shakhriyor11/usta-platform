# Usta — mobil ilova dizayni uchun brif

> Bu fayl AI dizayn vositasiga (Figma AI, Google Stitch, v0, Uizard, Galileo, ChatGPT/Claude va h.k.)
> berish uchun yozilgan. Unda ilova nima qilishi, kimlar ishlatishi, qaysi ekranlar borligi,
> har ekranda qaysi ma'lumot ko'rinishi va qaysi tugmalar qachon faol bo'lishi yozilgan.
> Hamma ma'lumot mavjud backend (REST API) asosida olingan. Ekrandagi har bir maydon backend'da haqiqatan bor.
>
> **Qanday ishlatish:** faylni to'liq AI'ga bering va eng oxiridagi
> ["AI uchun tayyor promptlar"](#15-ai-uchun-tayyor-promptlar) bo'limidan boshlang. Keyin ekranlarni
> bittadan chizdiring (§8 va §9 dagi ekran raqamlari bo'yicha).

---

## 1. Mahsulot haqida qisqacha

**Usta** — O'zbekistondagi marketplace: mijoz uyidagi muammo uchun (jo'mrak oqyapti, rozetka ishlamayapti,
konditsioner o'rnatish kerak va h.k.) yaqin atrofdagi tekshirilgan **ustani** topadi va chaqiradi.

- Mijoz xizmatni tanlaydi, muammoni yozadi, manzilni ko'rsatadi. Tizim buyurtmani eng yaqin 5 ta mos ustaga
  **taklif** sifatida yuboradi. Birinchi qabul qilgan usta buyurtmani oladi (Yandex Go / Uber'ga o'xshash
  "taksi modeli"). Mijoz qidiruvdan ustani o'zi tanlashi ham mumkin.
- Usta keladi, ishni bajaradi va ilovada **yakuniy narxni** kiritadi. Mijoz ustaga **naqd** to'laydi.
- Mijoz ustani **1–5 yulduz** bilan baholaydi va sharh yozadi.
- Platforma ustadan har buyurtma uchun **10% komissiya** oladi (haftalik hisob, usta platformaga to'laydi).
- Buyurtma davomida mijoz va usta **chat** orqali yozishadi va bir-biriga **qo'ng'iroq** qila oladi.
- Muammo bo'lsa **shikoyat yoki nizo** ochiladi, uni admin hal qiladi.

**Bozor:** O'zbekiston, asosan Toshkent. **Valyuta:** UZS (so'm). **Vaqt zonasi:** Asia/Tashkent.
**Tillar:** o'zbek (lotin, standart), rus, ingliz. Ilovada til almashtirish bo'lishi kerak.

---

## 2. Foydalanuvchilar (rollar)

| Rol | Kim | Mobil ilovada |
|---|---|---|
| **Mijoz (CUSTOMER)** | Usta chaqiradigan oddiy odam, 20–60 yosh, texnikadan uzoq bo'lishi mumkin | ✅ Asosiy |
| **Usta (MASTER)** | Santexnik, elektrik, ta'mirchi. Ko'pincha ish joyida, qo'li band, tez qaror qiladi | ✅ Asosiy |
| **Admin** | Platforma xodimi | ❌ Mobil'da emas (alohida veb-panel) |

**Muhim:** bitta telefon raqam (bitta hisob) **ikkala rolga** ham ega bo'lishi mumkin. Tavsiya:
**bitta ilova** qiling va profil ichida **"Mijoz rejimi ⇄ Usta rejimi"** almashtirgichini qo'ying.
Ikki rejimda pastki navigatsiya va bosh sahifa har xil bo'ladi.

### Persona'lar
- **Dilnoza, 34, mijoz.** Ishlaydi, vaqti kam. Oshxonada quvur oqyapti, 1 soat ichida usta kerak.
  Narx va ustaning ishonchliligi (reyting, tasdiqlangan belgisi) uning uchun muhim.
- **Botir aka, 45, santexnik usta.** 15 yillik tajriba. Telefonni qo'lqopda ham bosa olishi kerak:
  tugmalar katta, matn kam bo'lsin. Yangi taklif kelganda **5 daqiqa** ichida javob berishi kerak.

---

## 3. Dizayn tamoyillari (tavsiya)

1. **Ishonch.** "Tasdiqlangan usta" belgisi, reyting, baholar soni va tajriba yillari hamma joyda yaqqol ko'rinsin.
2. **Tezlik.** Mijoz buyurtmani 3 qadamda bera olsin. Ustaning taklif ekrani bir qarashda tushunarli bo'lsin.
3. **Holat doim aniq.** Buyurtma qaysi bosqichda ekani, keyin nima bo'lishi va qancha vaqt qolgani ko'rinsin
   (taymer yoki progress).
4. **Katta tegish nuqtalari.** Kamida 48×48 dp, asosiy tugmalar ekran pastida, bir qo'l bilan bosiladigan joyda.
5. **Uch til.** Rus tilida matnlar ~30% uzunroq bo'ladi, layout bunga moslashsin.
6. **Light va dark rejim.**

### Taklif etilgan vizual uslub (o'zgartirish mumkin)
- Kayfiyat: zamonaviy, toza, do'stona, "uy va ishonch". Bolalarcha ham, korporativ-sovuq ham emas.
- Asosiy rang: ishonchli ko'k yoki yashil-ko'k (masalan `#1E6FFF` yoki `#0FA37F`), aksent rang to'q sariq
  (`#FF8A00`, "shoshilinch / harakat" uchun).
- Holat ranglari: muvaffaqiyat yashil, ogohlantirish sariq, xato qizil, neytral kulrang.
- Shrift: Inter / Manrope / SF Pro (lotin va kirill harflarini qo'llab-quvvatlashi shart).
- Burchaklar 12–16 dp, kartochkalar yengil soyali. Ikonkalar chiziqli (outline).
- Rasmlar: haqiqiy ustalar fotosi yoki yassi (flat) illyustratsiya.

---

## 4. Ma'lumot formatlari (ekranlarda qanday ko'rsatish)

| Ma'lumot | Format | Misol |
|---|---|---|
| Pul | bo'shliq bilan guruhlash + "so'm" | `150 000 so'm` |
| "Narx dan" | prefiks bilan | `150 000 so'm dan` |
| Telefon | `+998 XX XXX XX XX` | `+998 90 123 45 67` |
| Reyting | 1 yoki 2 kasr + yulduz + soni | `★ 4.8 (127)` |
| Masofa | km, 1 kasr | `2.4 km` |
| Sana/vaqt | Toshkent vaqti, nisbiy ham bo'lishi mumkin | `Bugun, 14:30` · `21-noyabr, 09:00` |
| Sharh muallifi | ism + familiya bosh harfi | `Aziz K.` |
| Taymer | daqiqa:soniya | `04:37` |

---

## 5. Katalog (seed ma'lumotlari, ikonkalar uchun)

Kategoriyalar daraxtsimon (3 darajagacha). Kategoriya va xizmat javobida:
- `iconUrl` — ko'rsatiladigan ikonka: tayyor to'plamdan SVG (nisbiy yo'l, `/api/v1/public/catalog/icons/…svg`,
  bir rangli chiziqli — rangini ilova beradi) yoki admin yuklagan PNG/WEBP (to'liq havola). `null` bo'lishi mumkin —
  standart ikonka ko'rsating.
- `imageUrl` — ixtiyoriy rasm (15 daqiqalik havola); yo'q bo'lsa kartochka faqat ikonka bilan.
- `name` — `Accept-Language` bo'yicha (`uz`, `uz-Cyrl`, `ru`, `en`).

| Ikonka kaliti | Kategoriya (uz / ru / en) | Xizmatlar misoli |
|---|---|---|
| `plumbing` | Santexnika / Сантехника / Plumbing | Jo'mrak o'rnatish · Quvur oqishini tuzatish · Unitaz o'rnatish · Kanalizatsiyani tozalash |
| `electrical` | Elektrika / Электрика / Electrical | Rozetka/viklyuchatel o'rnatish · Elektr simlarini tortish · Qandil o'rnatish |
| `repair` | Ta'mirlash / Ремонт / Repair | ↳ **Bo'yoq va devor ishlari**: Devor bo'yash, Gulqog'oz yopishtirish · ↳ **Kafel yotqizish**: Kafel yotqizish |
| `appliances` | Maishiy texnika / Бытовая техника / Home appliances | Kir yuvish mashinasini ta'mirlash · Muzlatgich ta'mirlash |
| `climate` | Konditsioner va isitish / Кондиционеры и отопление / HVAC | Konditsioner o'rnatish · Konditsionerga xizmat ko'rsatish |
| `furniture` | Mebel / Мебель / Furniture | Mebel yig'ish |

Admin keyin yangi kategoriyalar qo'shishi mumkin, shuning uchun kategoriya grid'i dinamik bo'lishi kerak.

---

## 6. Buyurtma hayot sikli (eng muhim qism)

```
CREATED ──► SEARCHING ──► ACCEPTED ──► IN_PROGRESS ──► COMPLETED ──► COMMISSION ──► REVIEWED
   │            │             │               │              │             │
   └─► CANCELLED◄┘◄────────────┘               └──► DISPUTED ◄┴─────────────┘
                │                                     │
                └─► EXPIRED                            └──► COMPLETED yoki CANCELLED (admin qarori)
```

| Status | Mijozga ko'rinadigan nom | Ustaga ko'rinadigan nom | Rang (tavsiya) | Ma'nosi |
|---|---|---|---|---|
| `CREATED` | Yaratildi | — | kulrang | Bir lahzalik, darhol SEARCHING'ga o'tadi |
| `SEARCHING` | Usta qidirilmoqda | Yangi taklif | ko'k (animatsiya) | Ustalarga taklif yuborildi. 15 daqiqa (rejalashtirilganda — vaqtidan 1 soat oldingacha). Usta voz kechsa buyurtma shu holatga qaytadi |
| `ACCEPTED` | Usta topildi | Qabul qilindi | yashil-ko'k | Usta qabul qildi va yo'lda. Telefonlar ochiladi, chat ishlaydi |
| `IN_PROGRESS` | Ish bajarilmoqda | Jarayonda | to'q sariq | Usta ishni boshladi |
| `COMPLETED` | Bajarildi | Bajarildi | yashil | Usta yakunladi (bir lahzalik, darhol COMMISSION'ga o'tadi) |
| `COMMISSION` | Bajarildi — baholang | Bajarildi | yashil | Yakuniy narx belgilandi, mijoz baholashi kutilmoqda |
| `REVIEWED` | Yakunlandi | Yakunlandi | yashil (to'q) | Mijoz baholadi, buyurtma yopildi |
| `CANCELLED` | Bekor qilindi | Bekor qilindi | qizil | Sabab `cancelReason` da |
| `EXPIRED` | Usta topilmadi | Muddati o'tdi | kulrang | Qidiruv muddatida hech kim qabul qilmadi |
| `DISPUTED` | Nizo ko'rib chiqilmoqda | Nizo | qizil-to'q sariq | Admin hal qiladi |

### Kim qachon nima qila oladi
| Amal | Kim | Qaysi statusda |
|---|---|---|
| Bekor qilish (sabab ixtiyoriy) | Mijoz | CREATED, SEARCHING, ACCEPTED |
| Voz kechish (**sabab majburiy**; buyurtma bekor bo'lmaydi, qayta qidiriladi) | Usta | faqat ACCEPTED |
| Ishni boshlash | Usta | ACCEPTED |
| Yakunlash (**yakuniy narx kiritadi**) | Usta | IN_PROGRESS |
| Nizo ochish (sabab majburiy) | Ikkalasi | IN_PROGRESS, COMPLETED, COMMISSION |
| Baholash (1–5 + izoh) | Mijoz | COMPLETED, COMMISSION (bir marta) |
| Chatda yozish | Ikkalasi | ACCEPTED … DISPUTED. Undan keyin chat faqat o'qish uchun qoladi |
| Bir-birining telefonini ko'rish | Ikkalasi | faqat ACCEPTED, IN_PROGRESS |
| Rasm/video biriktirish yoki o'chirish | Mijoz | CREATED, SEARCHING, ACCEPTED (ish boshlanguncha) |

### Taklif (usta tomonidan ko'rinadi) — `OfferStatus`
`PENDING` (javob kutilmoqda, **5 daqiqa**) · `ACCEPTED` · `REJECTED` (rad etdi) · `EXPIRED` (javob bermadi) ·
`CANCELLED` (boshqa usta oldi yoki mijoz bekor qildi).

Rad etilgan, muddati o'tgan yoki boshqa usta olgan taklifning buyurtmasi endi ochilmaydi (404) — ro'yxatdan olib
tashlang. Komissiya to'lovi muddati o'tgan ustaga taklif kelmaydi va qabul qilishda `MASTER_HAS_OVERDUE_DEBT`
qaytadi (moliya ekranida `overdue: true` banneri kerak).

Usta rad etsa, buyurtma keyingi ustalarga o'tadi. Ikki usta bir vaqtda "Qabul qilish"ni bossa, faqat bittasi
oladi, ikkinchisiga **"Bu buyurtmani boshqa usta qabul qilib bo'ldi"** xabari chiqadi. Bu holat uchun ham
dizayn kerak.

### Maxfiylik qoidasi (dizaynga ta'sir qiladi)
- Taklif bosqichida usta **faqat ko'cha manzili va masofani** ko'radi. Podyezd, xonadon, aniq nuqta va mijoz
  telefoni **qabul qilgandan keyin** ochiladi. UI'da buni "🔒 Qabul qilgandan keyin ochiladi" ko'rinishida
  bildiring.
- Mijoz ustaning aniq ish joyini ko'rmaydi, faqat ish radiusini va o'zigacha bo'lgan masofani ko'radi.
  **Shuning uchun xaritada ustalarni nuqta sifatida ko'rsatib bo'lmaydi.** Qidiruv natijalari ro'yxat
  ko'rinishida bo'ladi.

---

## 7. Umumiy oqim: kirish va ro'yxatdan o'tish (ikkala rol uchun)

### 7.1 Splash
Logo, yuklanish. Token bor bo'lsa, to'g'ridan-to'g'ri bosh sahifaga o'tadi.

### 7.2 Onboarding (2–3 slayd, ixtiyoriy)
1) "Yaqin atrofdagi tekshirilgan ustalar" 2) "Narxni oldindan biling" 3) "Baholang va xavfsiz to'lang". Til tanlash.

### 7.3 Telefon raqam kiritish
- `+998` prefiksi oldindan qo'yilgan, raqamlar uchun klaviatura, avtomatik formatlash.
- "Kod olish" tugmasi.
- Xatolar: "Telefon raqam formati noto'g'ri (+998XXXXXXXXX)", "So'rovlar soni juda ko'p, birozdan so'ng urinib ko'ring".

### 7.4 SMS kod (OTP)
- **5 xonali** kod, 5 ta alohida katakcha, SMS'dan avtomatik to'ldirish.
- Kod **2 daqiqa** amal qiladi. "Qayta yuborish" **60 soniyadan** keyin faol bo'ladi (taymer ko'rinadi).
- Maksimum **5 urinish**. Xato: "Tasdiqlash kodi noto'g'ri. Qolgan urinishlar: 3".
- Boshqa holatlar: "Kod muddati tugagan, yangi kod so'rang", "Urinishlar soni tugadi", "Hisobingiz bloklangan".
- "Raqamni o'zgartirish" havolasi.

### 7.5 Rol tanlash (faqat birinchi kirishda, rol yo'q bo'lsa)
Ikki katta kartochka: **"Menga usta kerak"** (Mijoz) va **"Men ustaman"** (Usta). Keyinroq profildan ikkinchi
rolni ham qo'shish mumkin.

### 7.6 Mijoz profilini yaratish
Ism-familiya (majburiy, 120 belgigacha), avatar (ixtiyoriy). → Bosh sahifa.

(Usta uchun alohida onboarding — §9.1.)

---

## 8. MIJOZ ilovasi

**Pastki navigatsiya (4–5 tab):** 🏠 Bosh sahifa · 📋 Buyurtmalarim · 🔔 Bildirishnomalar (o'qilmaganlar soni
badge'da) · 👤 Profil.

### M1. Bosh sahifa
- Yuqorida: salomlashuv ("Salom, Dilnoza"), **tanlangan manzil** (asosiy manzil, bosilsa manzil tanlash ochiladi).
- **Qidiruv maydoni:** "Qanday xizmat kerak? Masalan: jo'mrak". Xizmat nomi bo'yicha qidiradi.
- **Faol buyurtma banneri** (agar SEARCHING / ACCEPTED / IN_PROGRESS buyurtma bo'lsa): status, xizmat nomi,
  usta ismi, bosilsa buyurtma tafsiloti ochiladi.
- **Kategoriyalar grid'i** (ikonka va nom, 2×3 yoki 3×N).
- Ixtiyoriy: "Ommabop xizmatlar" gorizontal ro'yxati.
- "Baholanmagan buyurtma" eslatmasi (COMMISSION holatidagi buyurtma bo'lsa).

### M2. Kategoriya sahifasi
Kategoriya nomi; ichki kategoriyalar bo'lsa, chip yoki bo'limlar ko'rinishida; xizmatlar ro'yxati (nomi,
qisqa tavsif). Xizmat bosilsa → M3.

### M3. Xizmat sahifasi
- Xizmat nomi, tavsifi (uch tilda bo'lishi mumkin).
- Ikki asosiy harakat:
  - **"Tezkor chaqirish"** → M6 (tizim eng yaqin ustalarga o'zi yuboradi).
  - **"Ustani o'zim tanlayman"** → M4.

### M4. Ustalar qidiruvi (natijalar)
- Ustida: xizmat nomi, manzil (yoki joriy joylashuv), filtr tugmasi, saralash.
- **Saralash:** Reyting bo'yicha (standart) · Masofa bo'yicha · Narx bo'yicha.
- **Filtrlar (bottom sheet):** radius (1–100 km, standart 20) · minimal reyting (0–5) · narx oralig'i (dan–gacha) ·
  "Faqat hozir online".
- **Usta kartochkasi** ro'yxatda: avatar, ism, ✅ tasdiqlangan, `★ 4.8 (127)`, tajriba ("12 yil tajriba"),
  masofa ("2.4 km"), narx ("150 000 so'm dan"), 🟢 online nuqtasi.
- Sahifalash: pastga tushganda yana yuklanadi (bir sahifada 20 ta).
- Bo'sh holat: "Bu hududda mos usta topilmadi. Radiusni kattalashtiring yoki tezkor chaqiring".

### M5. Usta profili (ommaviy kartochka)
- Katta avatar, ism, ✅ "Tasdiqlangan usta", reyting va baholar soni, tajriba, ish radiusi ("10 km atrofida ishlaydi").
- **Bio** (2000 belgigacha, "Ko'proq" bilan yig'iladi).
- **Xizmatlar va narxlar:** ro'yxat: xizmat nomi, "dan" narx, ustaning izohi ("Materiallar mijozdan").
- **Portfolio:** rasmlar galereyasi (har rasmda izoh bo'lishi mumkin), bosilsa to'liq ekranda ochiladi.
- **Sharhlar:** `Aziz K.`, ★★★★★, izoh, sana; "Hammasini ko'rish" (sahifalangan).
- Pastda yopishqoq tugma: **"Shu ustaga buyurtma berish"** → M6 (usta oldindan tanlangan; taklif avval
  faqat unga boradi, u rad etsa boshqalarga o'tadi. Buni foydalanuvchiga qisqa izoh bilan ayting).

### M6. Buyurtma berish (1 ekran yoki 3 qadamli wizard)
1. **Xizmat** (tanlangan, o'zgartirish mumkin).
2. **Muammo tavsifi** (majburiy, 2000 belgigacha): "Oshxonadagi jo'mrak oqyapti, almashtirish kerak".
3. **Rasm/video** (ixtiyoriy, **5 tagacha**, JPEG/PNG/WEBP/MP4, 50 MB gacha): ustaga muammoni ko'rsatish uchun.
4. **Manzil:** saqlangan manzillardan tanlash **yoki** yangi manzil kiritish (xaritada pin + matn + "Podyezd,
   qavat, xonadon").
5. **Vaqt:** "Imkon qadar tez" (standart) **yoki** "Vaqtni tanlash" (hozirdan keyin, **30 kun ichida**).
6. Agar usta tanlangan bo'lsa: kichik usta kartochkasi.
7. Tugma: **"Usta chaqirish"**.

Xatolar: "Tanlangan usta bu xizmat yoki manzil uchun mavjud emas", "O'zingizga buyurtma bera olmaysiz",
"Rejalashtirilgan vaqt noto'g'ri".

### M7. Usta qidirilmoqda (SEARCHING) — to'liq ekran holati
- Animatsiya (radar yoki pulsatsiya), "Yaqin atrofdagi ustalarga yuborildi".
- **Qolgan vaqt taymeri** (`searchDeadline` gacha; rejalashtirilgan buyurtmada kunlar bo'lishi mumkin).
- **Taxminiy narx:** "90 000 – 140 000 so'm dan" (`estimatedPriceMin/Max`; usta topilmaguncha bo'sh bo'lishi
  mumkin).
- Buyurtma qisqacha: xizmat, manzil, vaqt.
- Tugma: "Buyurtmani bekor qilish" (sabab so'raladi, ixtiyoriy).
- Usta qabul qilganda push keladi va ekran avtomatik M8 ga o'tadi.

### M7b. Usta topilmadi (EXPIRED)
Illyustratsiya, "Afsuski, hech bir usta buyurtmani qabul qilmadi". Tugmalar: "Qaytadan urinish" (shu
ma'lumotlar bilan yangi buyurtma), "Ustani o'zim tanlayman".

### M8. Buyurtma tafsilotlari (status bo'yicha o'zgaradigan asosiy ekran)
- **Status sarlavhasi** (rang va ikonka bilan) va **progress / timeline:**
  Qidirilmoqda → Usta topildi → Ish boshlandi → Bajarildi → Baholandi. Har qadamda vaqt (status tarixidan).
- **Usta bloki** (ACCEPTED'dan boshlab): avatar, ism, reyting; ACCEPTED va IN_PROGRESS'da **📞 Qo'ng'iroq** va
  **💬 Chat** tugmalari.
- **Narx:** "Usta narxi: 150 000 so'm dan" → yakunlangandan keyin "**Yakuniy narx: 180 000 so'm** — ustaga
  naqd to'lang".
- **Tafsilotlar:** xizmat, tavsif, manzil (+ podyezd/xonadon), vaqt, biriktirilgan rasmlar.
- Bekor qilingan bo'lsa: sabab.
- **Harakatlar (statusga qarab):**
  - SEARCHING / ACCEPTED → "Bekor qilish".
  - COMPLETED / COMMISSION → katta **"Ustani baholang ★"** tugmasi.
  - IN_PROGRESS / COMPLETED / COMMISSION → "Muammo bormi?" menyusi → Nizo ochish / Shikoyat.
  - REVIEWED → qo'ygan bahosi ko'rinadi.
  - DISPUTED → banner: "Nizo administrator tomonidan ko'rib chiqilmoqda".

### M9. Baholash
Usta avatari va ismi, 5 ta katta yulduz, izoh (2000 belgigacha, ixtiyoriy), tez tanlanadigan teglar (ixtiyoriy
dizayn g'oyasi: "Tez", "Sifatli", "Toza", "Xushmuomala"). "Yuborish" tugmasi. Muvaffaqiyat ekrani.

### M10. Buyurtmalarim (ro'yxat)
- Tablar: **Faol** (SEARCHING, ACCEPTED, IN_PROGRESS, DISPUTED) · **Tarix** (qolganlari).
- Kartochka: xizmat nomi, status chip, manzil, sana/vaqt, usta ismi, narx (`price` yoki `priceFrom ... dan`).
- Bo'sh holat: "Hali buyurtma yo'q" + "Usta chaqirish" tugmasi.

### M11. Chat (buyurtma ichida)
- Sarlavha: ikkinchi tomonning ismi, buyurtma xizmati, 📞 tugma (faol bo'lsa).
- Xabarlar pufakchalari (matn, 2000 belgigacha), vaqt, **o'qildi ✓✓** (ikkinchi tomon qachongacha o'qiganiga qarab).
- Real vaqt (WebSocket). Eski xabarlar yuqoriga scroll qilganda yuklanadi.
- Yozish mumkin bo'lmasa (buyurtma yopilgan): pastda "Buyurtma yopilgan — chat faqat o'qish uchun" banneri.
- **Rasm yuborish** (kamera yoki galereya, JPEG/PNG/WEBP, 10 MB, ixtiyoriy izoh): `POST /orders/{id}/chat/images`.
  Xabarda `imageUrl` bo'lsa rasm pufakchasi (bosilganda to'liq ekran); havola 15 daqiqalik — muddati o'tsa xabarlarni
  qayta yuklang. Yuklanish jarayoni va xato holati kerak.

### M12. Shikoyat yuborish
- Buyurtma (avtomatik), **sabab:** Sifat yomon (`QUALITY`) · Kelmadi (`NO_SHOW`) · Narx bo'yicha (`PRICE`) ·
  Xulq-atvor (`BEHAVIOR`) · Firibgarlik (`FRAUD`) · Boshqa (`OTHER`).
- Tavsif (majburiy, 2000 belgigacha).
- Switch: **"Nizo ochish"**: buyurtma to'xtatiladi va admin narx yoki bekor qilish bo'yicha qaror qiladi
  (ogohlantiruvchi izoh bilan).
- **Mening shikoyatlarim:** ro'yxat, status: Ochiq / Ko'rib chiqilmoqda / Hal qilindi / Rad etildi, admin izohi.

### M13. Bildirishnomalar
Ro'yxat (yangilari tepada), o'qilmaganlari ajratib ko'rsatiladi, "Hammasini o'qildi" tugmasi. Bosilsa tegishli
buyurtma ochiladi. Turi bo'yicha ikonka (§10).

### M14. Profil
- Avatar, ism, telefon. "Tahrirlash".
- **Manzillarim** → M15.
- **Mening shikoyatlarim**.
- **Til:** O'zbekcha / Русский / English.
- **Usta rejimiga o'tish** yoki **"Usta bo'lish"** (MASTER roli yo'q bo'lsa).
- Yordam / Biz bilan bog'lanish, Foydalanish shartlari.
- **Chiqish**.

### M15. Manzillar
- Ro'yxat (**10 tagacha**): nomi ("Uy", "Ish"), manzil, "Asosiy" belgisi. Swipe bilan o'chirish, "Asosiy qilish".
- **Manzil qo'shish/tahrirlash:** xarita (pin markazda, "Joriy joylashuvim" tugmasi), nomi (60 belgi),
  manzil matni (500), qo'shimcha: podyezd, qavat, xonadon (255), "Asosiy manzil" switch. Birinchi manzil
  avtomatik asosiy bo'ladi.

---

## 9. USTA ilovasi

**Pastki navigatsiya:** 🏠 Bosh sahifa · 📥 Takliflar (badge bilan) · 🛠 Buyurtmalarim · 💰 Moliya · 👤 Profil.

### 9.1 Usta onboarding (verifikatsiya), 4 qadamli wizard, progress ko'rinadi

**U1. Shaxsiy ma'lumot**
Ism-familiya (majburiy), avatar, o'zi haqida (bio, 2000 belgigacha), tajriba yillari (0–70).

**U2. Ish hududi**
Xaritada ish markazini belgilash (pin) va **radius slider** (1–200 km, standart ~10). Xaritada aylana
ko'rinadi. Verifikatsiyaga yuborish uchun majburiy.

**U3. Xizmatlar va narxlar**
Katalogdan xizmatlarni tanlash (**50 tagacha**). Har xizmat uchun: **"narx dan" (so'm)**, izoh ("Materiallar
mijozdan"), faol/nofaol switch.

Verifikatsiyaga yuborilgandan keyin (PENDING, APPROVED) **ism va avatar o'zgartirilmaydi** (`IDENTITY_LOCKED`) —
maydonlarni qulflangan holda ko'rsating.

**U4. Hujjatlar**
- Majburiy: **Pasport** (`PASSPORT`), **Selfi** (`SELFIE`). Ixtiyoriy: Sertifikat (`CERTIFICATE`), Boshqa (`OTHER`).
- JPEG/PNG/WEBP/PDF, 10 MB gacha, jami 10 tagacha. Har hujjat kartochkasida: preview, turi, o'chirish.
- Tugma: **"Tekshiruvga yuborish"**. Xatolar: "Majburiy hujjatlar yetishmayapti: SELFIE", "Ish hududini kiriting".

**U5. Verifikatsiya holati ekrani**
| Holat | Ko'rinish |
|---|---|
| `NOT_SUBMITTED` | "Profilni to'ldiring" + qadamlar checklist'i |
| `PENDING` | ⏳ "Hujjatlaringiz tekshirilmoqda". Bu paytda hujjatlarni o'zgartirib bo'lmaydi |
| `APPROVED` | ✅ "Tabriklaymiz! Endi buyurtmalar qabul qila olasiz" |
| `REJECTED` | ❌ "Rad etildi" + **admin sababi** + "Tuzatib qayta yuborish" |

Tasdiqlanmagan usta mijozlarga ko'rinmaydi va taklif olmaydi. Bosh sahifada doim banner tursin.

### U6. Bosh sahifa (dashboard)
- **Katta Online/Offline switch** (eng asosiy element). Online bo'lganda ilova har ~30 soniyada signal
  yuboradi. Offline bo'lsa "faqat online" filtri bilan qidiruvda ko'rinmaydi.
- Verifikatsiya banneri (APPROVED bo'lmasa).
- **Yangi takliflar** (soni + birinchisi kartochka ko'rinishida).
- **Faol buyurtmalar** (ACCEPTED, IN_PROGRESS).
- Qisqa statistika: reyting `★ 4.8 (127)`, ushbu hafta komissiyasi, balans (qarz bo'lsa qizil).

### U7. Yangi taklif — to'liq ekran / modal (push'dan ochiladi)
- Katta **taymer** (5 daqiqadan teskari sanash, progress halqa).
- Xizmat nomi, **masofa** ("2.4 km"), **ko'cha manzili** (aniq manzil 🔒 yopiq), vaqt ("Imkon qadar tez" yoki
  sana), muammo tavsifi, mijoz rasmlari (galereya).
- Ikki katta tugma: **"Qabul qilish"** (asosiy, yashil) va **"Rad etish"**.
- Holatlar: "Bu buyurtmani boshqa usta qabul qilib bo'ldi", "Taklif muddati tugagan", "Hisobingiz tasdiqlanmagan".

### U8. Takliflar ro'yxati
Javob kutilayotgan takliflar kartochkalari: xizmat, masofa, manzil, qolgan vaqt (har birida mini taymer).

### U9. Buyurtmalarim (ro'yxat)
Tablar: **Faol** (ACCEPTED, IN_PROGRESS, DISPUTED) · **Tarix**. Kartochka: xizmat, status, manzil, vaqt, mijoz
ismi, narx.

### U10. Buyurtma tafsilotlari (usta)
- Status va timeline.
- **Mijoz bloki:** ism, **📞 Qo'ng'iroq** (faqat ACCEPTED / IN_PROGRESS), **💬 Chat**.
- **To'liq manzil** + podyezd/xonadon + kichik xarita + **"Yo'l ko'rsatish"** (Google/Yandex xarita ilovasini ochadi).
- Masofa, vaqt, tavsif, rasmlar.
- Narx: "Sizning narxingiz: 150 000 so'm dan"; yakunlangandan keyin yakuniy narx va **komissiya** ("Komissiya 10%: 18 000 so'm").
- **Harakatlar statusga qarab:**
  - ACCEPTED → katta **"Ishni boshlash"**; ikkinchi darajali "Bekor qilish" (sabab **majburiy**).
  - IN_PROGRESS → katta **"Ishni yakunlash"** → U11.
  - IN_PROGRESS / COMPLETED / COMMISSION → "Nizo ochish" / "Shikoyat".

### U11. Ishni yakunlash (bottom sheet)
- **Yakuniy narx** kiritish (so'm, katta raqamli klaviatura), ostida "Siz bergan narx: 150 000 so'm dan".
- Avtomatik hisob: "Platforma komissiyasi (10%): 18 000 so'm".
- Eslatma: "Mijoz sizga naqd to'laydi".
- Tugma: "Yakunlash".

### U12. Moliya
- **Balans kartochkasi:** balans (manfiy bo'lsa "Platformaga qarzingiz: 54 000 so'm", qizil), **to'lanishi
  kerak** (yopilgan haftalar bo'yicha, `invoicedDue`), **joriy hafta komissiyasi** (`accrued`), joriy davr
  ("18–24-noyabr haftasi").
- Izoh bloki: "Komissiya haftalik hisoblanadi. Hafta yopilgach 7 kun ichida to'lang". To'lov hozircha
  ofisda yoki admin orqali qabul qilinadi, ilovada to'lash tugmasi yo'q. Kelajakda Click/Payme/Uzum tugmasi
  uchun joy qoldiring.
- **Komissiyalar** (tab): buyurtma, buyurtma summasi, foiz, komissiya, hafta, status chip:
  Yig'ilmoqda (`ACCRUED`) · To'lov kutilmoqda (`INVOICED`) · To'langan (`PAID`) · Bekor qilingan (`CANCELLED`).
- **Tranzaksiyalar** (tab, ledger): turi (Komissiya yechildi −, Komissiya qaytarildi +, To'lov qabul qilindi +,
  Tuzatish), summa (+ yashil / − qizil), sana, havola.

### U13. Xizmatlarim
Ro'yxat: xizmat nomi, narx dan, faol switch, tahrirlash, o'chirish. Agar xizmatni admin o'chirgan bo'lsa:
"Xizmat katalogda faol emas" ogohlantirishi. "+ Xizmat qo'shish" (katalogdan tanlash).

### U14. Portfolio
Rasmlar grid'i (**30 tagacha**, JPEG/PNG/WEBP, 10 MB), har biriga izoh, qo'shish va o'chirish.

### U15. Mening sharhlarim
Reyting xulosasi (o'rtacha, soni), sharhlar ro'yxati.

### U16. Usta profili
Avatar, ism, verifikatsiya holati, reyting, ma'lumotlarni tahrirlash (U1–U2), xizmatlar, portfolio, hujjatlar,
til, **"Mijoz rejimiga o'tish"**, chiqish.

Chat, shikoyat va bildirishnomalar ekranlari mijozdagi bilan bir xil (M11–M13).

---

## 10. Bildirishnoma turlari (push va in-app)

| Tur | Kimga | Sarlavha | Matn misoli |
|---|---|---|---|
| `NEW_OFFER` | Usta | Yangi buyurtma | 2.4 km uzoqlikda yangi buyurtma. Javob berish uchun 5 daqiqa vaqtingiz bor |
| `ORDER_ACCEPTED` | Mijoz | Usta topildi | Usta buyurtmangizni qabul qildi. Bog'lanish uchun buyurtmani oching |
| `ORDER_STARTED` | Mijoz | Ish boshlandi | Usta ishni boshladi |
| `ORDER_COMPLETED` | Mijoz | Ish yakunlandi | Usta ishni yakunladi. Yakuniy narx: 180000.00 UZS. Rozi bo'lmasangiz nizo oching, aks holda ustani baholang |
| `ORDER_REOPENED` | Mijoz | Yangi usta qidirilmoqda | Usta buyurtmadan voz kechdi. Biz boshqa usta qidiryapmiz |
| `ORDER_CANCELLED` | Ikkalasi | Buyurtma bekor qilindi | Sababini buyurtma tafsilotlarida ko'ring |
| `ORDER_EXPIRED` | Mijoz | Usta topilmadi | Hech bir usta qabul qilmadi. Qaytadan urinib ko'ring |
| `ORDER_DISPUTED` | Ikkalasi | Nizo ochildi | Administrator ko'rib chiqadi |
| `DISPUTE_RESOLVED` | Ikkalasi | Nizo hal qilindi | Batafsil — buyurtmada |
| `NEW_REVIEW` | Usta | Yangi baho | Mijoz sizga 5 baho qo'ydi |
| `COMMISSION_INVOICED` | Usta | Haftalik komissiya | O'tgan hafta 12 ta buyurtma uchun komissiya: 216 000 UZS. To'lov muddati: 01.12 |
| `COMMISSION_SETTLED` | Usta | To'lov qabul qilindi | 18-noyabr haftasi uchun 216 000 UZS to'lovingiz qabul qilindi |
| `CHAT_MESSAGE` | Ikkalasi | Yangi xabar | (xabar matni; izohsiz rasmda "Rasm yuborildi"). Faqat push, in-app ro'yxatda chiqmaydi |
| `COMPLAINT_RESOLVED` | Muallif | Shikoyat ko'rib chiqildi | Batafsil — shikoyatlar bo'limida |
| `COMPLAINT_REJECTED` | Muallif | Shikoyat rad etildi | Sababini shikoyatlar bo'limida ko'ring |

Har bildirishnoma bosilganda `data.orderId` bo'yicha buyurtma ochiladi.

Chiqishda (`POST /auth/logout`) shu qurilmaning `pushToken` ini ham yuboring — aks holda push kelaveradi.

---

## 11. Umumiy holatlar (har ekran uchun chizilsin)

- **Yuklanmoqda:** skeleton kartochkalar (spinner emas).
- **Bo'sh holat:** illyustratsiya + 1 jumla + harakat tugmasi.
- **Xato:** backend matnni foydalanuvchi tilida qaytaradi. Uni toast/snackbar'da (oddiy xatolar) yoki inline
  (forma maydoni ostida) ko'rsating. Ichki xatoda: "Xatolik yuz berdi" + "Qayta urinish".
- **Internet yo'q:** yuqorida banner, oxirgi ma'lumotlar ko'rinib turadi.
- **Hisob bloklangan:** to'liq ekran: "Hisobingiz bloklangan. Qo'llab-quvvatlash xizmatiga murojaat qiling".
- **Sessiya tugagan:** telefon ekraniga qaytaradi.
- **O'chirilgan hisob:** administrator hisobni o'chirsa, ilova sessiyani tozalab telefon ekraniga qaytadi.
  Suhbatdosh o'chirilgan bo'lsa ismi "O'chirilgan foydalanuvchi" yoki bo'sh keladi.
- **Ruxsatlar:** joylashuv (manzil va usta ish hududi uchun), kamera/galereya (rasmlar va hujjatlar uchun),
  bildirishnomalar. Har biri uchun tushuntiruvchi oldindan ekran (pre-permission).
- **Tasdiqlash dialoglari:** bekor qilish, nizo ochish, hujjat yoki manzilni o'chirish, chiqish.

---

## 12. Ekranlar ro'yxati (checklist)

**Umumiy:** Splash · Onboarding · Telefon · OTP · Rol tanlash · Til tanlash · Bloklangan · Internet yo'q
**Mijoz:** M1 Bosh · M2 Kategoriya · M3 Xizmat · M4 Qidiruv + Filtr sheet · M5 Usta profili · M5b Barcha sharhlar ·
M6 Buyurtma berish · M7 Qidirilmoqda · M7b Topilmadi · M8 Buyurtma (har status uchun variant) · M9 Baholash ·
M10 Buyurtmalarim · M11 Chat · M12 Shikoyat + ro'yxat · M13 Bildirishnomalar · M14 Profil · M15 Manzillar +
manzil xaritasi
**Usta:** U1–U4 Onboarding · U5 Verifikatsiya holati (4 variant) · U6 Dashboard (online/offline) · U7 Yangi taklif ·
U8 Takliflar · U9 Buyurtmalarim · U10 Buyurtma (har status uchun variant) · U11 Yakunlash sheet · U12 Moliya
(3 tab) · U13 Xizmatlarim + xizmat qo'shish · U14 Portfolio · U15 Sharhlarim · U16 Profil

**Komponentlar (design system):** tugmalar (primary/secondary/destructive/ghost) · input, OTP katakcha, telefon
input, narx input · status chip (10 status) · usta kartochkasi (ro'yxat va kichik variant) · buyurtma kartochkasi ·
taklif kartochkasi + taymer · reyting yulduzlari (ko'rsatish va kiritish) · ✅ verified badge · online nuqta ·
timeline/stepper · chat pufakchasi · bottom sheet · toast · bo'sh holat · skeleton · tab bar · rasm galereya ·
xarita pin va radius aylanasi · hujjat yuklash kartochkasi.

---

## 13. Hozircha YO'Q narsalar (dizayn qilmang yoki "keyin" deb belgilang)

- Ilova ichida onlayn to'lov (Click/Payme/Uzum): faqat naqd. Kelajak uchun joy qoldirish mumkin.
- Chatda ovozli xabar, video va fayl: faqat matn va rasm.
- Ustalarni xaritada nuqta sifatida ko'rsatish (maxfiylik sababli).
- Narx bo'yicha savdolashish yoki mijozning o'z narxini taklif qilishi: usta "narx dan" belgilaydi, yakuniy narxni usta kiritadi.
- Sevimli ustalar, promo-kodlar, referal: backend'da yo'q.
- Admin paneli: mobil emas.

---

## 14. Qisqa lug'at (UI matnlari uchun)

| Termin | uz | ru | en |
|---|---|---|---|
| Usta | Usta | Мастер | Pro / Handyman |
| Buyurtma | Buyurtma | Заказ | Order |
| Taklif | Taklif | Предложение | Offer |
| Narx dan | … so'm dan | от … сум | from … UZS |
| Tasdiqlangan | Tasdiqlangan | Проверен | Verified |
| Nizo | Nizo | Спор | Dispute |
| Shikoyat | Shikoyat | Жалоба | Complaint |
| Komissiya | Komissiya | Комиссия | Commission |

---

## 15. AI uchun tayyor promptlar

### 15.1 Asosiy prompt (birinchi bo'lib bering)

```
Design a modern mobile app (iOS + Android, 390×844) called "Usta" — an on-demand handyman marketplace
for Uzbekistan (plumbers, electricians, repair, appliance repair, AC, furniture assembly).
One app, two modes: Customer and Master (handyman); a user can switch modes in Profile.

Core flow (Customer): pick a service → describe the problem + photos → choose address on map → "Call a master"
→ the system sends the order to the 5 nearest verified masters (like ride-hailing, 15-minute search with a timer)
→ a master accepts → customer sees master card, can call and chat → master starts and finishes the job and enters
the final price → customer pays cash → rates 1–5 stars. Customer can also browse masters (list with rating,
distance, "from" price, online dot, verified badge) and order from a specific one.

Core flow (Master): onboarding wizard (profile, work area on map with radius slider, services with "from" prices,
upload passport + selfie) → verification status (pending/approved/rejected with reason) → big Online/Offline
toggle → full-screen incoming offer with 5-minute countdown, distance, street address (exact address locked
until accepted), Accept / Reject → order details with call, chat, navigation → Start job → Finish job sheet
(enter final price, shows 10% platform commission) → Finance tab (balance/debt, weekly commission, transactions).

Style: clean, trustworthy, friendly; primary blue #1E6FFF, accent orange #FF8A00, success green, error red;
Inter font (Latin + Cyrillic); 12–16 rounded corners; soft shadows; outline icons; large 48dp touch targets;
light and dark themes. UI language: Uzbek (Latin) — e.g. "Usta chaqirish", "Buyurtmalarim", "Qabul qilish".
Currency format: "150 000 so'm". Phone: "+998 90 123 45 67". Rating: "★ 4.8 (127)".
Order statuses with colored chips: Usta qidirilmoqda, Usta topildi, Ish bajarilmoqda, Bajarildi — baholang,
Yakunlandi, Bekor qilindi, Usta topilmadi, Nizo.
Start with a design system (colors, type scale, buttons, inputs, status chips, master card, order card) and then
the screens listed below.
```

### 15.2 Ekran bo'yicha prompt namunasi

```
Using the Usta design system, design screen M8 "Buyurtma tafsilotlari" (customer order details) in status
ACCEPTED: top status header "Usta topildi" with green-blue chip and a horizontal 5-step progress
(Qidirilmoqda ✓, Usta topildi ●, Ish boshlandi, Bajarildi, Baholandi) with timestamps; master block with avatar,
name "Botir Usmonov", verified badge, "★ 4.9 (212)", and two buttons "Qo'ng'iroq" and "Chat"; price row
"Usta narxi: 150 000 so'm dan"; details: service "Jo'mrak o'rnatish", description, address
"Chilonzor 9-kvartal, 12-uy · 3-podyezd, 45-xonadon", time "Bugun, 14:30", 2 photo thumbnails;
bottom secondary destructive button "Buyurtmani bekor qilish". Also show variants for IN_PROGRESS,
COMMISSION (big "Ustani baholang ★" CTA and final price "180 000 so'm — ustaga naqd to'lang"), and CANCELLED.
```

Shu andoza bo'yicha §8 va §9 dagi har bir ekran tavsifini (M1…M15, U1…U16) promptga qo'yib, ketma-ket chizdiring.
