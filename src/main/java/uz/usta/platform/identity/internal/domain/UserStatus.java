package uz.usta.platform.identity.internal.domain;

public enum UserStatus {
    ACTIVE,
    BLOCKED,
    /** Anonimlashtirilgan — qaytarib bo'lmaydi. */
    DELETED
}
