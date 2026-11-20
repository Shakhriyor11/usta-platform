package uz.usta.platform.order.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.persistence.AuditableEntity;

/**
 * Buyurtma agregati. Status faqat {@link #moveTo} orqali o'zgaradi (jadval {@link OrderStatus} da);
 * tarix va eventlarni application qatlamidagi {@code OrderTransitions} yozadi.
 * {@code location} (geography) DB'da GENERATED — bu yerda map qilinmagan.
 */
@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "master_id")
    private UUID masterId;

    @Column(name = "preferred_master_id", updatable = false)
    private UUID preferredMasterId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "address_title", length = 60, updatable = false)
    private String addressTitle;

    @Column(name = "address_text", nullable = false, length = 500, updatable = false)
    private String addressText;

    @Column(name = "address_details", length = 255, updatable = false)
    private String addressDetails;

    @Column(name = "latitude", nullable = false, updatable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false, updatable = false)
    private double longitude;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "search_deadline", nullable = false)
    private Instant searchDeadline;

    @Column(name = "price_from", precision = 14, scale = 2)
    private BigDecimal priceFrom;

    @Column(name = "price", precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "commission_amount", precision = 14, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    protected Order() {
    }

    public Order(UUID customerId, UUID serviceId, UUID preferredMasterId, String description, String addressTitle,
                 String addressText, String addressDetails, double latitude, double longitude, Instant scheduledAt,
                 Instant searchDeadline) {
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.preferredMasterId = preferredMasterId;
        this.description = description;
        this.addressTitle = addressTitle;
        this.addressText = addressText;
        this.addressDetails = addressDetails;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.searchDeadline = searchDeadline;
    }

    /**
     * Status o'tishi. Terminal holatga o'tganda {@code closedAt} belgilanadi.
     *
     * @return oldingi status
     * @throws BusinessException INVALID_STATUS_TRANSITION
     */
    public OrderStatus moveTo(OrderStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, status.name(), next.name());
        }
        OrderStatus previous = status;
        status = next;
        switch (next) {
            case ACCEPTED -> acceptedAt = now;
            case IN_PROGRESS -> startedAt = now;
            case COMPLETED -> completedAt = completedAt == null ? now : completedAt;
            default -> {
            }
        }
        if (next.isTerminal()) {
            closedAt = now;
        }
        return previous;
    }

    public void assignMaster(UUID masterId, BigDecimal priceFrom) {
        this.masterId = masterId;
        this.priceFrom = priceFrom;
    }

    public void setFinalPrice(BigDecimal price) {
        this.price = price.setScale(2, RoundingMode.HALF_UP);
    }

    public void recordCommission(BigDecimal amount) {
        this.commissionAmount = amount;
    }

    public void recordCancellation(UUID by, String reason) {
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    public boolean isOwnedBy(UUID userId) {
        return customerId.equals(userId);
    }

    public boolean isAssignedTo(UUID userId) {
        return masterId != null && masterId.equals(userId);
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public UUID getPreferredMasterId() {
        return preferredMasterId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getAddressTitle() {
        return addressTitle;
    }

    public String getAddressText() {
        return addressText;
    }

    public String getAddressDetails() {
        return addressDetails;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSearchDeadline() {
        return searchDeadline;
    }

    public BigDecimal getPriceFrom() {
        return priceFrom;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }
}
