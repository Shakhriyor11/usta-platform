package uz.usta.platform.chat.internal.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket orqali yuboriladigan hodisa ({@code /topic/orders.{orderId}.chat}).
 *
 * @param type    MESSAGE — yangi xabar ({@code message} to'ldirilgan); READ — {@code readerId} {@code readAt} gacha o'qidi
 */
@Schema(description = "WebSocket hodisasi: MESSAGE yoki READ")
public record ChatEvent(String type, MessageView message, UUID readerId, Instant readAt) {

    public static ChatEvent message(MessageView m) {
        return new ChatEvent("MESSAGE", m, null, null);
    }

    public static ChatEvent read(UUID readerId, Instant at) {
        return new ChatEvent("READ", null, readerId, at);
    }

    public record MessageView(UUID id, UUID orderId, UUID senderId, String content, Instant createdAt) {
    }
}
