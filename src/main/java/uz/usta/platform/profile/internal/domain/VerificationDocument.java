package uz.usta.platform.profile.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import uz.usta.platform.shared.persistence.BaseEntity;

@Entity
@Table(name = "master_verification_documents")
public class VerificationDocument extends BaseEntity {

    @Column(name = "master_id", nullable = false, updatable = false)
    private UUID masterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, updatable = false, length = 16)
    private DocumentType type;

    @Column(name = "file_key", nullable = false, updatable = false, length = 512)
    private String fileKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationDocument() {
    }

    public VerificationDocument(UUID masterId, DocumentType type, String fileKey, String contentType, long sizeBytes) {
        this.masterId = masterId;
        this.type = type;
        this.fileKey = fileKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public UUID getMasterId() {
        return masterId;
    }

    public DocumentType getType() {
        return type;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
