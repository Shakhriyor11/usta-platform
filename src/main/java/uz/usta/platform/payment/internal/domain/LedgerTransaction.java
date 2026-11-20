package uz.usta.platform.payment.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import uz.usta.platform.shared.persistence.BaseEntity;

/**
 * Ustaning platforma oldidagi hisobidagi o'zgarmas yozuv ({@code transactions}). Balans = yig'indi:
 * manfiy — qarz. Xatoni tuzatish faqat teskari yozuv bilan (DB trigger UPDATE/DELETE'ni taqiqlaydi).
 */
@Entity
@Immutable
@Table(name = "transactions")
public class LedgerTransaction extends BaseEntity {

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 24)
    private TransactionType type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference", updatable = false, length = 128)
    private String reference;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    private LedgerTransaction(UUID masterId, UUID orderId, TransactionType type, BigDecimal amount, String reference,
                              UUID createdBy) {
        this.masterId = masterId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.reference = reference;
        this.createdBy = createdBy;
    }

    public static LedgerTransaction charge(UUID masterId, UUID orderId, BigDecimal commission) {
        return new LedgerTransaction(masterId, orderId, TransactionType.COMMISSION_CHARGE, commission.negate(),
                "order:" + orderId, null);
    }

    public static LedgerTransaction reversal(UUID masterId, UUID orderId, BigDecimal commission, UUID actorId) {
        return new LedgerTransaction(masterId, orderId, TransactionType.COMMISSION_REVERSAL, commission,
                "order:" + orderId, actorId);
    }

    public static LedgerTransaction payment(UUID masterId, BigDecimal amount, String reference, UUID adminId) {
        return new LedgerTransaction(masterId, null, TransactionType.COMMISSION_PAYMENT, amount, reference, adminId);
    }

    public UUID getMasterId() {
        return masterId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
