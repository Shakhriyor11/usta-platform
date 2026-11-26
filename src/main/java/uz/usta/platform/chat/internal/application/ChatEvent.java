package uz.usta.platform.chat.internal.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket orqali yuboriladigan hodisa ({@code /topic/orders.{orderId}.chat}).
 *
 * @param type    MESSAGE — yangi xabar ({@code message} to'ldirilgan); READ — {@code readerId} {@code readAt} gacha o'qidi
 */
@Schema(description = "WebSocket hodisasi (/topic/orders.{orderId}.chat)")
public record ChatEvent(
        @Schema(allowableValues = {"MESSAGE", "READ"}) String type,
        @Schema(description = "type=MESSAGE da to'ldiriladi") MessageView message,
        @Schema(description = "type=READ: kim o'qidi") UUID readerId,
        @Schema(description = "type=READ: qaysi vaqtgacha o'qidi") Instant readAt) {

    public static ChatEvent message(MessageView m) {
        return new ChatEvent("MESSAGE", m, null, null);
    }

    public static ChatEvent read(UUID readerId, Instant at) {
        return new ChatEvent("READ", null, readerId, at);
    }

    /** @param imageUrl rasmli xabarda — vaqtinchalik (15 daq) havola; {@code content} — izoh, bo'sh bo'lishi mumkin */
    public record MessageView(
            UUID id, UUID orderId, UUID senderId,
            @Schema(description = "Matn yoki rasm izohi; izohsiz rasmda bo'sh satr") String content,
            @Schema(description = "Rasmli xabarda 15 daqiqalik havola, aks holda null", nullable = true) String imageUrl,
            Instant createdAt) {
    }
}
