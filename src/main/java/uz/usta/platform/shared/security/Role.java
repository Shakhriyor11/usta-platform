package uz.usta.platform.shared.security;

/** Platforma rollari. JWT'da {@code roles} claim'i sifatida, Spring Security'da {@code ROLE_*} sifatida. */
public enum Role {
    CUSTOMER,
    MASTER,
    /** Admin panel; qaysi bo'limlarga kirishi {@link AdminPermission} bilan belgilanadi. */
    ADMIN,
    /** Barcha huquqlar + adminlarni boshqarish. Faqat konfiguratsiya orqali ({@code SUPER_ADMIN_PHONES}). */
    SUPER_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
