package uz.usta.platform.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

@Entity
@Table(name = "master_portfolio")
public class PortfolioItem extends BaseEntity {

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Column(name = "file_key", nullable = false, updatable = false, length = 512)
    private String fileKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PortfolioItem() {
    }

    public PortfolioItem(UUID masterId, String fileKey, String contentType, long sizeBytes, String description) {
        this.masterId = masterId;
        this.fileKey = fileKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.description = description;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
