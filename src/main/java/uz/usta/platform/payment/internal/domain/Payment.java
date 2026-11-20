package uz.usta.platform.payment.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.usta.platform.shared.persistence.AuditableEntity;

/** Mijozning buyurtma uchun to'lovi (hozir — naqd, ustaga). */
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16)
    private PaymentMethod method;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {
    }

    public Payment(UUID orderId, UUID masterId, PaymentMethod method, String provider) {
        this.orderId = orderId;
        this.masterId = masterId;
        this.method = method;
        this.provider = provider;
        this.status = PaymentStatus.PENDING;
        this.amount = BigDecimal.ZERO;
    }

    public void record(BigDecimal amount, PaymentStatus status, String providerRef, Instant now) {
        this.amount = amount;
        this.status = status;
        this.providerRef = providerRef;
        this.paidAt = status == PaymentStatus.PAID ? now : paidAt;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
