Usta chaqirish platformasi backend API.

**Token olish (Authorize):**
1. `POST /api/v1/auth/otp/request` — `{"phone":"+998901234567"}`. Lokal/docker'da SMS yuborilmaydi: kod logda —
   `docker compose logs api | grep "SMS stub"` (IntelliJ'da — konsolda).
2. `POST /api/v1/auth/otp/verify` — `{"phone":"...","code":"12345"}` → `accessToken`.
3. Birinchi kirishda rol: `POST /api/v1/me/roles` — `{"role":"CUSTOMER"}` yoki `MASTER`; javobdagi **yangi**
   `accessToken` ni oling (rollar token ichida).
4. Yuqoridagi **Authorize** tugmasi → tokenni kiriting (`Bearer` so'zisiz). Token sahifa yangilanganda saqlanadi,
   15 daqiqa amal qiladi (`/api/v1/auth/refresh`).

**Super admin** — `SUPER_ADMIN_PHONES` dagi raqam bilan kiradi; adminlarni va ularning huquqlarini u tayinlaydi
("Super admin: Admins" bo'limi). Har admin endpoint'ining tavsifida kerakli huquq yozilgan.

**Umumiy qoidalar:** xatolar — RFC 9457 (`application/problem+json`), mijoz ilova `code` ga tayansin. Matnlar tili —
`Accept-Language` sarlavhasi (`uz` standart, `uz-Cyrl`, `ru`, `en`). Vaqt — UTC (ISO-8601), pul — UZS.
Real vaqt chat — STOMP over WebSocket `/ws` ("Chat" bo'limi).

Endpoint belgilari: 🆕 — yangi, ♻️ — o'zgargan.
