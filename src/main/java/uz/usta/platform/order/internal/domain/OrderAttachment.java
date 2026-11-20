package uz.usta.platform.order.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

/** Buyurtmaga biriktirilgan rasm yoki video. */
@Entity
@Table(name = "order_attachments")
public class OrderAttachment extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "file_key", nullable = false, updatable = false, length = 512)
    private String fileKey;

    @Column(name = "media_type", nullable = false, updatable = false, length = 100)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderAttachment() {
    }

    public OrderAttachment(UUID orderId, String fileKey, String mediaType, long sizeBytes) {
        this.orderId = orderId;
        this.fileKey = fileKey;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
