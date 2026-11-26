package uz.usta.platform.notification.internal.application;

import java.util.List;

/** Bildirishnoma turlari. Matn: {@code notification.<key>.title|body} (i18n), argumentlar {@code args} da saqlanadi. */
public enum NotificationType {
    NEW_OFFER("new-offer"),
    ORDER_ACCEPTED("order-accepted"),
    /** Biriktirilgan usta voz kechdi — yangisi qidirilmoqda. */
    ORDER_REOPENED("order-reopened"),
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

    /**
     * @param args saqlangan argumentlar: narxsiz eski "ish yakunlandi" yozuvlari o'z matnida qoladi; bo'sh chat
     *             matni — izohsiz rasm
     */
    public String bodyKey(List<String> args) {
        String suffix = ".body";
        if (this == ORDER_COMPLETED && !args.isEmpty()) {
            suffix = ".body-priced";
        } else if (this == CHAT_MESSAGE && args.equals(List.of(""))) {
            suffix = ".body-image";
        }
        return "notification." + key + suffix;
    }
}
