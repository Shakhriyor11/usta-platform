package uz.usta.platform.complaint.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.persistence.AuditableEntity;

@Entity
@Table(name = "complaints")
public class Complaint extends AuditableEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "against_user_id", nullable = false, updatable = false)
    private UUID againstUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 16)
    private ComplaintReason reason;

    @Column(name = "description", nullable = false, updatable = false, length = 2000)
    private String description;

    /** Shikoyat bilan birga buyurtma bo'yicha nizo ochilgan (orders.status = DISPUTED). */
    @Column(name = "dispute", nullable = false, updatable = false)
    private boolean dispute;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Complaint() {
    }

    public Complaint(UUID orderId, UUID authorId, UUID againstUserId, ComplaintReason reason, String description,
                     boolean dispute) {
        this.orderId = orderId;
        this.authorId = authorId;
        this.againstUserId = againstUserId;
        this.reason = reason;
        this.description = description;
        this.dispute = dispute;
    }

    public void take(UUID adminId) {
        if (status != ComplaintStatus.OPEN) {
            throw new BusinessException(ComplaintErrorCode.INVALID_STATE, status.name());
        }
        status = ComplaintStatus.IN_REVIEW;
        assignedTo = adminId;
    }

    public void close(ComplaintStatus result, UUID adminId, String note, Instant now) {
        if (!status.isOpen() || result.isOpen()) {
            throw new BusinessException(ComplaintErrorCode.INVALID_STATE, status.name());
        }
        status = result;
        resolvedBy = adminId;
        resolutionNote = note;
        resolvedAt = now;
        if (assignedTo == null) {
            assignedTo = adminId;
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getAgainstUserId() {
        return againstUserId;
    }

    public ComplaintReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDispute() {
        return dispute;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
