package uz.usta.platform.shared.security;

/**
 * Admin huquqlari — super admin har bir adminga alohida beradi. Super adminda hammasi bor.
 * Spring Security'da {@code PERM_*} authority; qaysi yo'l qaysi huquqni talab qilishi — {@code SecurityConfig}.
 */
public enum AdminPermission {
    /** Foydalanuvchilarni ko'rish, bloklash. */
    USERS,
    /** Ustalarni tasdiqlash/rad etish. */
    MASTERS,
    /** Kategoriya va xizmatlar, ularning rasm va ikonkalari. */
    CATALOG,
    /** Buyurtmalar va nizolar. */
    ORDERS,
    /** Komissiya, hisob-fakturalar, to'lovlar, solishtirish. */
    FINANCE,
    /** Shikoyatlar. */
    COMPLAINTS,
    /** Qidiruv indeksi, outbox. */
    SYSTEM,
    /** O'chirish (foydalanuvchi, kategoriya, xizmat) — tegishli bo'lim huquqiga qo'shimcha. */
    DELETE;

    public String authority() {
        return "PERM_" + name();
    }
}
