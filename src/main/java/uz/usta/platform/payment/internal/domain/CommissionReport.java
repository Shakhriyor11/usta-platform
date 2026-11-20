package uz.usta.platform.payment.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uz.usta.platform.shared.persistence.AuditableEntity;

/** Bitta buyurtma komissiyasi va u tegishli hisob davri (hafta). */
@Entity
@Table(name = "commission_reports")
public class CommissionReport extends AuditableEntity {

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "base_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "rate_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "period", nullable = false, updatable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommissionStatus status = CommissionStatus.ACCRUED;

    @Column(name = "invoiced_at")
    private Instant invoicedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected CommissionReport() {
    }

    public CommissionReport(UUID masterId, UUID orderId, BigDecimal baseAmount, BigDecimal ratePercent,
                            BigDecimal amount, LocalDate period) {
        this.masterId = masterId;
        this.orderId = orderId;
        this.baseAmount = baseAmount;
        this.ratePercent = ratePercent;
        this.amount = amount;
        this.period = period;
    }

    public void cancel() {
        this.status = CommissionStatus.CANCELLED;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPeriod() {
        return period;
    }

    public CommissionStatus getStatus() {
        return status;
    }

    public Instant getInvoicedAt() {
        return invoicedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
