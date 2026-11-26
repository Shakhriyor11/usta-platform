package uz.usta.platform.chat.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import uz.usta.platform.shared.persistence.BaseEntity;

@Entity
@Immutable
@Table(name = "messages")
public class ChatMessage extends BaseEntity {

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false, updatable = false, length = 2000)
    private String content;

    /** Ilova vaqti (DB default emas) — xabar darhol, flush'siz efirga uzatiladi. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Rasm ({@code content} — ixtiyoriy izoh, bo'sh bo'lishi mumkin). */
    @Column(name = "image_key", updatable = false, length = 512)
    private String imageKey;

    @Column(name = "image_type", updatable = false, length = 100)
    private String imageType;

    @Column(name = "image_size", updatable = false)
    private Long imageSize;

    protected ChatMessage() {
    }

    public ChatMessage(UUID chatId, UUID senderId, String content, Instant createdAt) {
        this.chatId = chatId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public void attachImage(String key, String type, long size) {
        this.imageKey = key;
        this.imageType = type;
        this.imageSize = size;
    }

    public String getImageKey() {
        return imageKey;
    }

    public UUID getChatId() {
        return chatId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
