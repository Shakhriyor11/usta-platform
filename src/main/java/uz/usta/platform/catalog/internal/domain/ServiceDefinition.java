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

/** Katalogdagi xizmat turi ({@code services} jadvali), masalan "Jo'mrak o'rnatish". */
@Entity
@Table(name = "services")
public class ServiceDefinition extends AuditableEntity {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

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

    @Column(name = "description_uz_cyrl", length = 2000)
    private String descriptionUzCyrl;

    @Embedded
    private CatalogMedia media;

    @Column(name = "description_uz", length = 2000)
    private String descriptionUz;

    @Column(name = "description_ru", length = 2000)
    private String descriptionRu;

    @Column(name = "description_en", length = 2000)
    private String descriptionEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    /** O'chirilgan (arxivlangan) xizmat: eski buyurtmalar nomini ko'rsatish uchun saqlanadi. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ServiceDefinition() {
    }

    public ServiceDefinition(UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                             int sortOrder, CatalogStatus status) {
        update(categoryId, slug, name, description, sortOrder, status);
    }

    public final void update(UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                             int sortOrder, CatalogStatus status) {
        this.categoryId = categoryId;
        this.slug = slug;
        this.nameUz = name.uz();
        this.nameRu = name.ru();
        this.nameEn = name.en();
        this.nameUzCyrl = name.uzCyrl();
        this.descriptionUzCyrl = description == null ? null : description.uzCyrl();
        this.descriptionUz = description == null ? null : description.uz();
        this.descriptionRu = description == null ? null : description.ru();
        this.descriptionEn = description == null ? null : description.en();
        this.sortOrder = sortOrder;
        this.status = status;
    }

    public LocalizedText getName() {
        return new LocalizedText(nameUz, nameRu, nameEn, nameUzCyrl);
    }

    public LocalizedText getDescription() {
        return descriptionUz == null && descriptionRu == null && descriptionEn == null && descriptionUzCyrl == null
                ? null : new LocalizedText(descriptionUz, descriptionRu, descriptionEn, descriptionUzCyrl);
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

    public UUID getCategoryId() {
        return categoryId;
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
