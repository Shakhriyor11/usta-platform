package uz.usta.platform.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox'dan yetkazilgan xabar. Yetkazish "at-least-once" — iste'molchilar {@link #id()} bo'yicha
 * idempotent bo'lishi kerak.
 *
 * @param payload JSON matn ko'rinishidagi event tanasi
 */
public record OutboxMessage(UUID id, String aggregateType, UUID aggregateId, String eventType,
                            String payload, Instant createdAt, int attempt) {
}
