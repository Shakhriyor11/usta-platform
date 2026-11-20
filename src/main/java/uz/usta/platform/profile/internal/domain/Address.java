package uz.usta.platform.profile.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import uz.usta.platform.shared.persistence.AuditableEntity;

/** Mijoz manzili. {@code geo_point} (geography) DB'da latitude/longitude'dan hisoblanadi. */
@Entity
@Table(name = "addresses")
public class Address extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "details", length = 255)
    private String details;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected Address() {
    }

    public Address(UUID userId, String title, String address, String details, double latitude, double longitude) {
        this.userId = userId;
        update(title, address, details, latitude, longitude);
    }

    public final void update(String title, String address, String details, double latitude, double longitude) {
        this.title = title;
        this.address = address;
        this.details = details;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void markDefault() {
        this.defaultAddress = true;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getAddress() {
        return address;
    }

    public String getDetails() {
        return details;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }
}
