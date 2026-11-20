package uz.usta.platform.chat;

import java.util.UUID;

/** Chat modulining outbox eventlari (notification — qabul qiluvchiga push). */
public final class ChatEvents {

    public static final String AGGREGATE = "chat";
    public static final String MESSAGE_SENT = "chat.message-sent";

    private ChatEvents() {
    }

    /** @param preview xabar boshi (push uchun, 100 belgigacha) */
    public record MessageSent(UUID messageId, UUID orderId, UUID senderId, UUID recipientId, String preview) {
    }
}
