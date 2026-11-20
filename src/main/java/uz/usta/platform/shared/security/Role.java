package uz.usta.platform.shared.security;

/** Platforma rollari. JWT'da {@code roles} claim'i sifatida, Spring Security'da {@code ROLE_*} sifatida. */
public enum Role {
    CUSTOMER,
    MASTER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
