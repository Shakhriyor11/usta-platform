package uz.usta.platform.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.shared.persistence.AuditableEntity;

@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "name_uz", nullable = false, length = 120)
    private String nameUz;

    @Column(name = "name_ru", length = 120)
    private String nameRu;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(name = "name_uz_cyrl", length = 120)
    private String nameUzCyrl;

    @Embedded
    private CatalogMedia media;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    /** O'chirilgan (arxivlangan) kategoriya: tarix uchun saqlanadi, hech qayerda ko'rsatilmaydi. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Category() {
    }

    public Category(UUID parentId, String slug, LocalizedText name, int sortOrder, CatalogStatus status) {
        update(parentId, slug, name, sortOrder, status);
    }

    public final void update(UUID parentId, String slug, LocalizedText name, int sortOrder, CatalogStatus status) {
        this.parentId = parentId;
        this.slug = slug;
        this.nameUz = name.uz();
        this.nameRu = name.ru();
        this.nameEn = name.en();
        this.nameUzCyrl = name.uzCyrl();
        this.sortOrder = sortOrder;
        this.status = status;
    }

    public LocalizedText getName() {
        return new LocalizedText(nameUz, nameRu, nameEn, nameUzCyrl);
    }

    public void markDeleted(Instant now) {
        status = CatalogStatus.INACTIVE;
        deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public CatalogMedia getMedia() {
        if (media == null) { // barcha ustunlar bo'sh bo'lsa Hibernate null qo'yadi
            media = new CatalogMedia();
        }
        return media;
    }

    public boolean isActive() {
        return status == CatalogStatus.ACTIVE;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getSlug() {
        return slug;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public CatalogStatus getStatus() {
        return status;
    }
}
