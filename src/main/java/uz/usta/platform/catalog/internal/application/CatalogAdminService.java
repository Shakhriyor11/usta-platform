package uz.usta.platform.catalog.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.CatalogEvents;
import uz.usta.platform.catalog.internal.domain.CatalogErrorCode;
import uz.usta.platform.catalog.internal.domain.CatalogMedia;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.CategoryHierarchy;
import uz.usta.platform.catalog.internal.domain.MasterServicePrice;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.MasterServicePriceRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FileStorage;

@Service
public class CatalogAdminService {

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;
    private final MasterServicePriceRepository prices;
    private final IconLibrary icons;
    private final FileStorage storage;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final CatalogProperties props;
    private final Clock clock;

    CatalogAdminService(CategoryRepository categories, ServiceDefinitionRepository services,
                        MasterServicePriceRepository prices, IconLibrary icons, FileStorage storage,
                        OutboxPublisher outbox, AuditLog audit, CatalogProperties props, Clock clock) {
        this.categories = categories;
        this.services = services;
        this.prices = prices;
        this.icons = icons;
        this.storage = storage;
        this.outbox = outbox;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /** @param icon to'plamdagi ikonka kaliti yoki {@code null} */
    public record CategoryCommand(UUID parentId, String slug, LocalizedText name, String icon, int sortOrder,
                                  CatalogStatus status) {
    }

    /** @param icon to'plamdagi ikonka kaliti yoki {@code null} */
    public record ServiceCommand(UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                                 String icon, int sortOrder, CatalogStatus status) {
    }

    // ---------------------------------------------------------------- kategoriyalar

    @Transactional(readOnly = true)
    public List<Category> allCategories() {
        return categories.findByDeletedAtIsNullOrderBySortOrderAscNameUzAsc();
    }

    @Transactional
    public Category createCategory(UUID adminId, CategoryCommand cmd) {
        if (categories.existsBySlugAndDeletedAtIsNull(cmd.slug())) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        validateParent(null, cmd.parentId());
        Category category = new Category(cmd.parentId(), cmd.slug(), cmd.name(), cmd.sortOrder(), cmd.status());
        applyIcon(category.getMedia(), cmd.icon());
        Category saved = categories.saveAndFlush(category);
        changed(adminId, "category.create", "category", saved.getId());
        return saved;
    }

    @Transactional
    public Category updateCategory(UUID adminId, UUID id, CategoryCommand cmd) {
        Category category = category(id);
        if (categories.existsBySlugAndIdNotAndDeletedAtIsNull(cmd.slug(), id)) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        validateParent(id, cmd.parentId());
        category.update(cmd.parentId(), cmd.slug(), cmd.name(), cmd.sortOrder(), cmd.status());
        applyIcon(category.getMedia(), cmd.icon());
        Category saved = categories.saveAndFlush(category);
        changed(adminId, "category.update", "category", id);
        return saved;
    }

    /**
     * Kategoriyani o'chirish (arxivlash). Ichida kategoriya yoki xizmat bo'lsa, faqat {@code cascade} bilan —
     * butun shox o'chadi.
     */
    @Transactional
    public void deleteCategory(UUID adminId, UUID id, boolean cascade) {
        Category root = category(id);
        Map<UUID, List<Category>> children = allCategories().stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<Category> subtree = new ArrayList<>(List.of(root));
        for (int i = 0; i < subtree.size(); i++) {
            subtree.addAll(children.getOrDefault(subtree.get(i).getId(), List.of()));
        }
        List<ServiceDefinition> inside = services.findByCategoryIdInAndDeletedAtIsNull(
                subtree.stream().map(Category::getId).toList());
        int nested = subtree.size() - 1;
        if (!cascade && (nested > 0 || !inside.isEmpty())) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_EMPTY,
                    Map.of("categories", nested, "services", inside.size()), nested, inside.size());
        }
        archive(inside);
        Instant now = Instant.now(clock);
        subtree.forEach(c -> {
            c.getMedia().clearFiles().forEach(storage::deleteAfterCommit);
            c.markDeleted(now);
        });
        audit.record(adminId, "catalog.category.delete", "category", id,
                Map.of("slug", root.getSlug(), "nestedCategories", nested, "services", inside.size()));
        publishChanged("category", id);
    }

    private void validateParent(UUID categoryId, UUID parentId) {
        if (parentId != null && !categories.existsByIdAndDeletedAtIsNull(parentId)) {
            throw BusinessException.notFound("Category", parentId);
        }
        Map<UUID, UUID> parentOf = new HashMap<>();
        allCategories().forEach(c -> parentOf.put(c.getId(), c.getParentId()));
        new CategoryHierarchy(parentOf).validateMove(categoryId, parentId, props.maxCategoryDepth());
    }

    private Category category(UUID id) {
        return categories.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("Category", id));
    }

    // ---------------------------------------------------------------- xizmatlar

    @Transactional(readOnly = true)
    public List<ServiceDefinition> services(UUID categoryId) {
        return categoryId == null
                ? services.findByDeletedAtIsNullOrderBySortOrderAscNameUzAsc()
                : services.findByCategoryIdAndDeletedAtIsNullOrderBySortOrderAscNameUzAsc(categoryId);
    }

    @Transactional
    public ServiceDefinition createService(UUID adminId, ServiceCommand cmd) {
        category(cmd.categoryId());
        if (services.existsBySlugAndDeletedAtIsNull(cmd.slug())) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        ServiceDefinition service = new ServiceDefinition(cmd.categoryId(), cmd.slug(), cmd.name(), cmd.description(),
                cmd.sortOrder(), cmd.status());
        applyIcon(service.getMedia(), cmd.icon());
        ServiceDefinition saved = services.saveAndFlush(service);
        changed(adminId, "service.create", "service", saved.getId());
        return saved;
    }

    @Transactional
    public ServiceDefinition updateService(UUID adminId, UUID id, ServiceCommand cmd) {
        ServiceDefinition service = service(id);
        category(cmd.categoryId());
        if (services.existsBySlugAndIdNotAndDeletedAtIsNull(cmd.slug(), id)) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        service.update(cmd.categoryId(), cmd.slug(), cmd.name(), cmd.description(), cmd.sortOrder(), cmd.status());
        applyIcon(service.getMedia(), cmd.icon());
        ServiceDefinition saved = services.saveAndFlush(service);
        changed(adminId, "service.update", "service", id);
        return saved;
    }

    /** Xizmatni o'chirish (arxivlash): eski buyurtmalar nomini ko'rsatishda davom etadi. */
    @Transactional
    public void deleteService(UUID adminId, UUID id) {
        ServiceDefinition service = service(id);
        archive(List.of(service));
        audit.record(adminId, "catalog.service.delete", "service", id, Map.of("slug", service.getSlug()));
        publishChanged("service", id);
    }

    /** Ustalarning shu xizmatlarga narxlari va xizmat fayllari o'chadi. */
    private void archive(List<ServiceDefinition> doomed) {
        if (doomed.isEmpty()) {
            return;
        }
        List<MasterServicePrice> offered = prices.findByServiceIdIn(
                doomed.stream().map(ServiceDefinition::getId).toList());
        prices.deleteAll(offered);
        offered.stream().map(MasterServicePrice::getMasterId).distinct().forEach(masterId -> outbox.publish(
                "master_profile", masterId, CatalogEvents.MASTER_SERVICES_CHANGED,
                new CatalogEvents.MasterServicesChanged(masterId)));
        Instant now = Instant.now(clock);
        doomed.forEach(s -> {
            s.getMedia().clearFiles().forEach(storage::deleteAfterCommit);
            s.markDeleted(now);
        });
    }

    private ServiceDefinition service(UUID id) {
        return services.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> BusinessException.notFound("Service", id));
    }

    // ----------------------------------------------------------------

    private void applyIcon(CatalogMedia media, String icon) {
        // o'zgarmagan qiymat tekshirilmaydi: to'plam paydo bo'lishidan oldin kiritilgan ikonka saqlanib qoladi
        if (icon != null && !Objects.equals(icon, media.getIcon()) && !icons.contains(icon)) {
            throw new BusinessException(CatalogErrorCode.UNKNOWN_ICON, icon);
        }
        storage.deleteAfterCommit(media.choosePreset(icon));
    }

    private void changed(UUID adminId, String action, String entity, UUID id) {
        audit.record(adminId, "catalog." + action, entity, id, Map.of());
        publishChanged(entity, id);
    }

    private void publishChanged(String entity, UUID id) {
        outbox.publish("catalog", id, CatalogEvents.CATALOG_CHANGED, new CatalogEvents.CatalogChanged(entity, id));
    }
}
