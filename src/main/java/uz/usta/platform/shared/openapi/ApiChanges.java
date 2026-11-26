package uz.usta.platform.shared.openapi;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Joriy relizda qo'shilgan va o'zgargan endpoint'lar — Swagger'da belgilanadi (🆕 / ♻️) va bosh sahifadagi
 * jadvalga tushadi. Keyingi relizda ro'yxat (va {@code resources/openapi/changelog.md}) yangilanadi.
 * {@code OpenApiDocsIT} har yozuvdagi endpoint mavjudligini tekshiradi.
 */
final class ApiChanges {

    static final String RELEASE = "2026-11-26";

    enum Kind {
        NEW("🆕", "Yangi"),
        CHANGED("♻️", "Oʻzgardi");

        final String marker;
        final String label;

        Kind(String marker, String label) {
            this.marker = marker;
            this.label = label;
        }
    }

    record Change(Kind kind, HttpMethod method, String path, String note) {
    }

    static final List<Change> ALL = List.of(
            // ------------------------------------------------ super admin va foydalanuvchilar
            added(GET, "/admin/admins", "Adminlar va ularning huquqlari."),
            added(POST, "/admin/admins", "Telefon bo'yicha admin tayinlash; `permissions` — kamida bittasi."),
            added(GET, "/admin/admins/permissions", "Huquqlar ro'yxati, nomi `Accept-Language` tilida."),
            added(PUT, "/admin/admins/{id}", "Huquqlarni to'liq almashtirish. Adminning eski tokeni darhol "
                    + "`401 TOKEN_OUTDATED` oladi."),
            added(DELETE, "/admin/admins/{id}", "Adminlikni olib tashlash (mijoz/usta rollari qoladi)."),
            added(DELETE, "/admin/users/{id}", "Hisobni anonimlashtirish (qaytarib bo'lmaydi). 409: "
                    + "`USER_HAS_ACTIVE_ORDERS`, `USER_HAS_DEBT`, `CANNOT_DELETE_SELF`, `SUPER_ADMIN_IMMUTABLE`, "
                    + "`USER_DELETED`; 403: `ADMIN_REQUIRES_SUPER_ADMIN`."),
            changed(GET, "/admin/users", "O'chirilganlar faqat `status=DELETED` so'ralganda chiqadi (`phone` bo'sh); "
                    + "`role=SUPER_ADMIN` qo'shildi."),
            changed(POST, "/admin/users/{id}/block", "Adminni faqat super admin bloklaydi "
                    + "(`ADMIN_REQUIRES_SUPER_ADMIN`); super adminni bloklab bo'lmaydi (`CANNOT_BLOCK_ADMIN`)."),
            changed(POST, "/admin/users/{id}/unblock", "Adminni faqat super admin blokdan chiqaradi."),
            changed(GET, "/me", "`permissions` maydoni qo'shildi; `roles` da `SUPER_ADMIN` bo'lishi mumkin."),
            changed(POST, "/auth/otp/request", "Bir raqamga sutkasiga 10 ta SMS; oshsa `429`."),
            changed(POST, "/auth/otp/verify", "`user` ichida `permissions`. Sutkasiga 15 ta noto'g'ri kod; "
                    + "oshsa `429`."),
            changed(POST, "/auth/refresh", "`TOKEN_OUTDATED` kelganda shu yerdan yangi huquqlar bilan token olinadi."),
            changed(POST, "/auth/logout", "`pushToken` yuboring — shu qurilmaga push to'xtaydi."),

            // ------------------------------------------------ katalog
            added(GET, "/public/catalog/icons", "Tayyor ikonkalar: `key` ni kategoriya/xizmatning `icon` maydoniga "
                    + "yozing; `url` — SVG."),
            added(GET, "/public/catalog/icons/{key}.svg", "Bir rangli chiziqli SVG (`currentColor`); keshlanadi."),
            changed(GET, "/public/catalog/categories", "`iconUrl`, `imageUrl` qo'shildi."),
            changed(GET, "/public/catalog/categories/{id}/services", "`icon`, `iconUrl`, `imageUrl` qo'shildi."),
            changed(GET, "/public/catalog/services/{id}", "`icon`, `iconUrl`, `imageUrl` qo'shildi."),
            changed(GET, "/public/catalog/services", "Qidiruv o'zbek kirillida ham ishlaydi; tutuq belgisining "
                    + "turi farqlanmaydi."),
            changed(GET, "/admin/catalog/categories", "`iconUrl`, `customIcon`, `imageUrl`, `name.uzCyrl`; "
                    + "o'chirilganlar chiqmaydi."),
            changed(POST, "/admin/catalog/categories", "`name.uz`, `name.ru`, `name.en` majburiy (kamida 2 belgi); "
                    + "`icon` — to'plamdagi kalit (`UNKNOWN_ICON`)."),
            changed(PUT, "/admin/catalog/categories/{id}", "Uch til majburiy. `icon` berilsa yuklangan ikonka "
                    + "o'chadi; `null` bo'lsa yuklangani qoladi."),
            added(DELETE, "/admin/catalog/categories/{id}", "O'chirish (arxivlash). Ichi bo'sh bo'lmasa `409 "
                    + "CATEGORY_NOT_EMPTY` (`categories`, `services` sonlari bilan) — tasdiqlatib `?cascade=true`."),
            added(PUT, "/admin/catalog/categories/{id}/image", "Rasm yuklash. Xatolar: `IMAGE_INVALID`, "
                    + "`IMAGE_DIMENSIONS`, `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`."),
            added(DELETE, "/admin/catalog/categories/{id}/image", "Rasmni olib tashlash."),
            added(PUT, "/admin/catalog/categories/{id}/icon", "O'z ikonkasi. Qo'shimcha xato: `IMAGE_NOT_SQUARE`."),
            added(DELETE, "/admin/catalog/categories/{id}/icon", "Yuklangan ikonkani olib tashlash."),
            changed(GET, "/admin/catalog/services", "`icon`, `iconUrl`, `customIcon`, `imageUrl`, `uzCyrl`; "
                    + "o'chirilganlar chiqmaydi."),
            changed(POST, "/admin/catalog/services", "`name` uch tilda majburiy; `icon` maydoni qo'shildi."),
            changed(PUT, "/admin/catalog/services/{id}", "`name` uch tilda majburiy; `icon` maydoni qo'shildi."),
            added(DELETE, "/admin/catalog/services/{id}", "O'chirish: ustalarning shu xizmatga narxlari ham o'chadi."),
            added(PUT, "/admin/catalog/services/{id}/image", "Rasm yuklash."),
            added(DELETE, "/admin/catalog/services/{id}/image", "Rasmni olib tashlash."),
            added(PUT, "/admin/catalog/services/{id}/icon", "O'z ikonkasi."),
            added(DELETE, "/admin/catalog/services/{id}/icon", "Yuklangan ikonkani olib tashlash."),

            // ------------------------------------------------ chat
            added(POST, "/orders/{orderId}/chat/images", "Rasm (`file`) va ixtiyoriy izoh (`caption`). Javob va "
                    + "WebSocket hodisasida `imageUrl`. Xatolar: `CHAT_CLOSED`, `IMAGE_INVALID`, `FILE_TOO_LARGE`."),
            changed(GET, "/orders/{orderId}/chat/messages", "`imageUrl` qo'shildi (rasmli xabarda `content` bo'sh "
                    + "bo'lishi mumkin). Chat har usta bilan alohida: usta almashsa tarix yangidan boshlanadi."),
            changed(POST, "/orders/{orderId}/chat/messages", "Ko'rinmas va boshqaruv belgilari rad etiladi."),

            // ------------------------------------------------ buyurtmalar
            changed(POST, "/customer/orders", "Soatiga 20 ta buyurtma (`429`). Rejalashtirilgan buyurtma vaqtidan "
                    + "1 soat oldingacha qidiriladi."),
            changed(GET, "/customer/orders/{id}", "`estimatedPriceMin/Max` (faqat `SEARCHING`). Tizim sabablari "
                    + "(`history[].reason`, `cancelReason`) so'rov tilida."),
            changed(POST, "/master/offers/{orderId}/accept", "Komissiya qarzi muddati o'tgan bo'lsa "
                    + "`403 MASTER_HAS_OVERDUE_DEBT`."),
            changed(GET, "/master/orders/{id}", "Rad etilgan, muddati o'tgan yoki boshqa usta olgan taklif buyurtmani ko'rsatmaydi (404)."),
            changed(POST, "/master/orders/{id}/complete", "`price` kamida 1 (aks holda `400 VALIDATION_FAILED`)."),
            changed(POST, "/master/orders/{id}/cancel", "Buyurtma bekor bo'lmaydi — `SEARCHING` ga qaytadi; mijozga "
                    + "`ORDER_REOPENED` bildirishnomasi. Javobda mijoz ma'lumotlari va aniq manzil yo'q."),
            changed(POST, "/admin/orders/{id}/resolve", "`price` kamida 1."),

            // ------------------------------------------------ profil, moliya, shikoyat, bildirishnoma
            changed(PUT, "/customer/profile", "`fullName` kamida 2 belgi."),
            changed(PUT, "/master/profile", "`fullName` kamida 2 belgi. Tekshiruvga yuborilgan/tasdiqlangan ustada "
                    + "ism o'zgarmaydi (`409 IDENTITY_LOCKED`)."),
            changed(PUT, "/master/profile/avatar", "Tekshiruvga yuborilgan/tasdiqlangan ustada "
                    + "`409 IDENTITY_LOCKED`. Rasm metama'lumotlari o'chiriladi."),
            changed(PUT, "/customer/profile/avatar", "Rasm tuzilishi tekshiriladi (`IMAGE_INVALID`), "
                    + "metama'lumotlari o'chiriladi."),
            changed(GET, "/master/finance/summary", "`overdue` qo'shildi — `true` bo'lsa ogohlantirish ko'rsating."),
            changed(GET, "/admin/finance/invoices", "`onHold`, `due` qo'shildi."),
            changed(POST, "/admin/finance/settlements", "`amount` = hisobdagi `due` (ortiqcha to'lov chegirilgan)."),
            changed(POST, "/complaints", "Sutkasiga 10 ta shikoyat (`429`)."),
            changed(GET, "/notifications", "Yangi tur: `ORDER_REOPENED`. Matn `uz-Cyrl` da ham."),
            changed(POST, "/push-tokens", "Qurilma tili `Accept-Language` dan olinadi (`uz-Cyrl` ham)."),

            // ------------------------------------------------ tizim
            added(GET, "/admin/outbox/dead", "Yetkazilmay qolgan ichki eventlar."),
            added(POST, "/admin/outbox/dead/retry", "Ularni qayta navbatga qo'yish."));

    private ApiChanges() {
    }

    private static Change added(HttpMethod method, String path, String note) {
        return new Change(Kind.NEW, method, "/api/v1" + path, note);
    }

    private static Change changed(HttpMethod method, String path, String note) {
        return new Change(Kind.CHANGED, method, "/api/v1" + path, note);
    }
}
