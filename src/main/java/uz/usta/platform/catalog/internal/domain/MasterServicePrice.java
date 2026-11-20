package uz.usta.platform.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import uz.usta.platform.shared.persistence.AuditableEntity;

/** Usta ko'rsatadigan xizmat va uning "narx dan" qiymati, UZS ({@code master_services}). */
@Entity
@Table(name = "master_services")
public class MasterServicePrice extends AuditableEntity {

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "price_from", nullable = false, precision = 14, scale = 2)
    private BigDecimal priceFrom;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected MasterServicePrice() {
    }

    public MasterServicePrice(UUID masterId, UUID serviceId) {
        this.masterId = masterId;
        this.serviceId = serviceId;
    }

    public void update(BigDecimal priceFrom, String description, boolean active) {
        this.priceFrom = priceFrom.setScale(2, RoundingMode.HALF_UP); // NUMERIC(14,2) bilan bir xil ko'rinish
        this.description = description;
        this.active = active;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public BigDecimal getPriceFrom() {
        return priceFrom;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
