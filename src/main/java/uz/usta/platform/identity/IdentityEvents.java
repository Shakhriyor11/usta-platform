package uz.usta.platform.identity;

import java.util.UUID;

/** Identity modulining outbox eventlari (boshqa modullar {@code OutboxMessage.eventType} bo'yicha tinglaydi). */
public final class IdentityEvents {

    public static final String AGGREGATE = "user";
    public static final String USER_BLOCKED = "identity.user-blocked";
    public static final String USER_UNBLOCKED = "identity.user-unblocked";
    public static final String ROLE_GRANTED = "identity.role-granted";

    private IdentityEvents() {
    }

    public record UserBlocked(UUID userId, String reason) {
    }

    public record UserUnblocked(UUID userId) {
    }

    public record RoleGranted(UUID userId, String role) {
    }
}
