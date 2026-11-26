package uz.usta.platform.order.internal.application;

/**
 * Tizim yozadigan bekor qilish/status o'zgarishi sabablari. Bazada kod saqlanadi, matn o'qish paytida foydalanuvchi
 * tilida chiqadi ({@code reason.*} i18n kalitlari).
 */
public final class SystemReasons {

    public static final String PREFIX = "system:";

    static final String CUSTOMER_BLOCKED = PREFIX + "customer-blocked";
    static final String MASTER_BLOCKED = PREFIX + "master-blocked";
    static final String CUSTOMER_DELETED = PREFIX + "customer-deleted";
    static final String MASTER_DELETED = PREFIX + "master-deleted";

    private SystemReasons() {
    }
}
