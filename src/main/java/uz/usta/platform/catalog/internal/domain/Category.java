package uz.usta.platform.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

    @Column(name = "icon", length = 255)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    protected Category() {
    }

    public Category(UUID parentId, String slug, LocalizedText name, String icon, int sortOrder, CatalogStatus status) {
        update(parentId, slug, name, icon, sortOrder, status);
    }

    public final void update(UUID parentId, String slug, LocalizedText name, String icon, int sortOrder,
                             CatalogStatus status) {
        this.parentId = parentId;
        this.slug = slug;
        this.nameUz = name.uz();
        this.nameRu = name.ru();
        this.nameEn = name.en();
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.status = status;
    }

    public LocalizedText getName() {
        return new LocalizedText(nameUz, nameRu, nameEn);
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

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public CatalogStatus getStatus() {
        return status;
    }
}
