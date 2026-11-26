package uz.usta.platform.order.internal.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Buyurtma status mashinasi. Ruxsat etilgan o'tishlar shu jadvalda; boshqa har qanday o'tish
 * {@code INVALID_STATUS_TRANSITION} beradi. {@code REJECTED} — buyurtma emas, taklif holati ({@link OfferStatus}).
 */
public enum OrderStatus {
    CREATED,
    SEARCHING,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    COMMISSION,
    REVIEWED,
    CANCELLED,
    EXPIRED,
    DISPUTED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(SEARCHING, CANCELLED),
            SEARCHING, EnumSet.of(ACCEPTED, EXPIRED, CANCELLED),
            ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELLED, SEARCHING), // SEARCHING — usta voz kechdi
            IN_PROGRESS, EnumSet.of(COMPLETED, DISPUTED),
            COMPLETED, EnumSet.of(COMMISSION, DISPUTED),
            COMMISSION, EnumSet.of(REVIEWED, DISPUTED),
            DISPUTED, EnumSet.of(COMPLETED, CANCELLED));

    /** Mijoz bekor qila oladigan holatlar. */
    public static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(CREATED, SEARCHING, ACCEPTED);
    /** Nizo ochish mumkin bo'lgan holatlar (ish boshlangandan keyin). */
    public static final Set<OrderStatus> DISPUTABLE = EnumSet.of(IN_PROGRESS, COMPLETED, COMMISSION);
    /** Mijoz biriktirma qo'sha oladigan holatlar (usta ishni boshlaguncha). */
    public static final Set<OrderStatus> EDITABLE = EnumSet.of(CREATED, SEARCHING, ACCEPTED);

    public boolean canTransitionTo(OrderStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }

    /** Usta biriktirilgan va ish faol (mijoz telefoni va aniq manzil ustaga ko'rinadi). */
    public boolean isActiveAssignment() {
        return this == ACCEPTED || this == IN_PROGRESS;
    }
}
