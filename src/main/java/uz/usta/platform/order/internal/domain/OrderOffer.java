package uz.usta.platform.order.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

/** Ustaga yuborilgan taklif ({@code order_master_offers}). */
@Entity
@Table(name = "order_master_offers")
public class OrderOffer extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_status", nullable = false, length = 16)
    private OfferStatus status = OfferStatus.PENDING;

    @Column(name = "distance_m", nullable = false)
    private int distanceMeters;

    /** Taklif yuborilgan paytdagi ustaning "narx dan" qiymati — mijozga taxminiy narx uchun. */
    @Column(name = "price_from", precision = 14, scale = 2)
    private BigDecimal priceFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected OrderOffer() {
    }

    public OrderOffer(UUID orderId, UUID masterId, int distanceMeters, BigDecimal priceFrom, Instant expiresAt) {
        this.orderId = orderId;
        this.masterId = masterId;
        this.distanceMeters = distanceMeters;
        this.priceFrom = priceFrom;
        this.expiresAt = expiresAt;
    }

    /** Boshqa usta qabul qilgani uchun yopilgan taklifni qayta ochish (u usta voz kechganda). */
    public void reopen(int distanceMeters, BigDecimal priceFrom, Instant expiresAt) {
        this.status = OfferStatus.PENDING;
        this.distanceMeters = distanceMeters;
        this.priceFrom = priceFrom;
        this.expiresAt = expiresAt;
        this.respondedAt = null;
    }

    public BigDecimal getPriceFrom() {
        return priceFrom;
    }

    public boolean isOpen(Instant now) {
        return status == OfferStatus.PENDING && expiresAt.isAfter(now);
    }

    public void respond(OfferStatus result, Instant now) {
        this.status = result;
        this.respondedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
