package uz.usta.platform.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Order modulining outbox eventlari (notification, keyinchalik chat/payment tinglaydi). */
public final class OrderEvents {

    public static final String AGGREGATE = "order";
    /** Har bir status o'tishi (yaratilish ham: {@code from = null}). */
    public static final String STATUS_CHANGED = "order.status-changed";
    /** Ustaga yangi taklif yuborildi. */
    public static final String OFFER_CREATED = "order.offer-created";

    private OrderEvents() {
    }

    /**
     * @param actorId kim o'zgartirdi; {@code null} — tizim (masalan muddat tugashi)
     * @param price   yakuniy narx, UZS — hali kiritilmagan bo'lsa {@code null}
     */
    public record StatusChanged(UUID orderId, UUID customerId, UUID masterId, String from, String to, UUID actorId,
                                String reason, BigDecimal price) {
    }

    public record OfferCreated(UUID orderId, UUID masterId, UUID serviceId, Instant expiresAt, int distanceMeters) {
    }
}
