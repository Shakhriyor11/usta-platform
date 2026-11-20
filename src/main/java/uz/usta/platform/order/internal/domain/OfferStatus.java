package uz.usta.platform.order.internal.domain;

/** Ustaga yuborilgan taklif holati. */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    /** Usta rad etdi — buyurtma SEARCHING'da qoladi va keyingi ustaga o'tadi. */
    REJECTED,
    /** Javob muddati tugadi. */
    EXPIRED,
    /** Boshqa usta qabul qildi yoki buyurtma bekor qilindi/muddati tugadi. */
    CANCELLED
}
