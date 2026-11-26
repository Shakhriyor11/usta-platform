package uz.usta.platform.identity;

import java.util.UUID;

/** Identity modulining outbox eventlari (boshqa modullar {@code OutboxMessage.eventType} bo'yicha tinglaydi). */
public final class IdentityEvents {

    public static final String AGGREGATE = "user";
    public static final String USER_BLOCKED = "identity.user-blocked";
    public static final String USER_UNBLOCKED = "identity.user-unblocked";
    public static final String ROLE_GRANTED = "identity.role-granted";
    /** Hisob anonimlashtirildi — har modul o'zidagi shaxsiy ma'lumot va fayllarni o'chiradi. */
    public static final String USER_DELETED = "identity.user-deleted";

    private IdentityEvents() {
    }

    public record UserBlocked(UUID userId, String reason) {
    }

    public record UserUnblocked(UUID userId) {
    }

    public record RoleGranted(UUID userId, String role) {
    }

    public record UserDeleted(UUID userId) {
    }
}
