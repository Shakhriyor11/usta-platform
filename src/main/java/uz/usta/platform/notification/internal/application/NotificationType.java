package uz.usta.platform.notification.internal.application;

/** Bildirishnoma turlari. Matn: {@code notification.<key>.title|body} (i18n), argumentlar {@code args} da saqlanadi. */
public enum NotificationType {
    NEW_OFFER("new-offer"),
    ORDER_ACCEPTED("order-accepted"),
    ORDER_STARTED("order-started"),
    ORDER_COMPLETED("order-completed"),
    ORDER_CANCELLED("order-cancelled"),
    ORDER_EXPIRED("order-expired"),
    ORDER_DISPUTED("order-disputed"),
    DISPUTE_RESOLVED("dispute-resolved"),
    NEW_REVIEW("new-review"),
    COMMISSION_INVOICED("commission-invoiced"),
    COMMISSION_SETTLED("commission-settled"),
    /** Faqat push (har xabar uchun in-app yozuv yaratilmaydi — chatning o'zida o'qilmaganlar soni bor). */
    CHAT_MESSAGE("chat-message"),
    COMPLAINT_RESOLVED("complaint-resolved"),
    COMPLAINT_REJECTED("complaint-rejected");

    private final String key;

    NotificationType(String key) {
        this.key = key;
    }

    public String titleKey() {
        return "notification." + key + ".title";
    }

    public String bodyKey() {
        return "notification." + key + ".body";
    }
}
