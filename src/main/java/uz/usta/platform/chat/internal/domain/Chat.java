package uz.usta.platform.chat.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

/** Buyurtma bo'yicha mijoz va usta o'rtasidagi chat (har buyurtmada bitta). */
@Entity
@Table(name = "chats")
public class Chat extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "customer_last_read_at")
    private Instant customerLastReadAt;

    @Column(name = "master_last_read_at")
    private Instant masterLastReadAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Chat() {
    }

    public Chat(UUID orderId, UUID customerId, UUID masterId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.masterId = masterId;
    }

    public UUID otherParty(UUID userId) {
        return userId.equals(customerId) ? masterId : customerId;
    }

    public void markRead(UUID userId, Instant at) {
        if (userId.equals(customerId)) {
            customerLastReadAt = at;
        } else if (userId.equals(masterId)) {
            masterLastReadAt = at;
        }
    }

    /** Ikkinchi tomon qachongacha o'qigan (o'qildi belgilari uchun). */
    public Instant lastReadBy(UUID userId) {
        return userId.equals(customerId) ? customerLastReadAt : masterLastReadAt;
    }

    public void messageAt(Instant at) {
        this.lastMessageAt = at;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
