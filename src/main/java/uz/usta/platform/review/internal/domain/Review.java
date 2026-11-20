package uz.usta.platform.review.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

/** Baho va sharh. O'zgarmas: 1 buyurtma = 1 baho ({@code uq_reviews_order}), reyting 1..5 (DB CHECK). */
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "rating", nullable = false, updatable = false)
    private short rating;

    @Column(name = "comment", length = 2000, updatable = false)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(UUID orderId, UUID customerId, UUID masterId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be 1..5");
        }
        this.orderId = orderId;
        this.customerId = customerId;
        this.masterId = masterId;
        this.rating = (short) rating;
        this.comment = comment;
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

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
