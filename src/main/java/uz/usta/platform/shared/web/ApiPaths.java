package uz.usta.platform.shared.web;

/** API yo'llari uchun yagona prefikslar. */
public final class ApiPaths {

    public static final String V1 = "/api/v1";
    public static final String PUBLIC = V1 + "/public";
    public static final String ADMIN = V1 + "/admin";
    public static final String AUTH = V1 + "/auth";
    /** Faqat MASTER roli uchun ("mening" usta resurslarim). */
    public static final String MASTER = V1 + "/master";
    /** Faqat CUSTOMER roli uchun. */
    public static final String CUSTOMER = V1 + "/customer";

    private ApiPaths() {
    }
}
