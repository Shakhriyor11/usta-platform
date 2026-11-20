package uz.usta.platform.catalog.internal.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.CatalogEvents;
import uz.usta.platform.catalog.internal.domain.CatalogErrorCode;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.CategoryHierarchy;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.shared.outbox.OutboxPublisher;

@Service
public class CatalogAdminService {

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final CatalogProperties props;

    CatalogAdminService(CategoryRepository categories, ServiceDefinitionRepository services, OutboxPublisher outbox,
                        AuditLog audit, CatalogProperties props) {
        this.categories = categories;
        this.services = services;
        this.outbox = outbox;
        this.audit = audit;
        this.props = props;
    }

    public record CategoryCommand(UUID parentId, String slug, LocalizedText name, String icon, int sortOrder,
                                  CatalogStatus status) {
    }

    public record ServiceCommand(UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                                 int sortOrder, CatalogStatus status) {
    }

    // ---------------------------------------------------------------- kategoriyalar

    @Transactional(readOnly = true)
    public List<Category> allCategories() {
        return categories.findAllByOrderBySortOrderAscNameUzAsc();
    }

    @Transactional
    public Category createCategory(UUID adminId, CategoryCommand cmd) {
        if (categories.existsBySlug(cmd.slug())) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        validateParent(null, cmd.parentId());
        Category saved = categories.saveAndFlush(new Category(cmd.parentId(), cmd.slug(), cmd.name(), cmd.icon(),
                cmd.sortOrder(), cmd.status()));
        changed(adminId, "category.create", "category", saved.getId());
        return saved;
    }

    @Transactional
    public Category updateCategory(UUID adminId, UUID id, CategoryCommand cmd) {
        Category category = categories.findById(id).orElseThrow(() -> BusinessException.notFound("Category", id));
        if (categories.existsBySlugAndIdNot(cmd.slug(), id)) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        validateParent(id, cmd.parentId());
        category.update(cmd.parentId(), cmd.slug(), cmd.name(), cmd.icon(), cmd.sortOrder(), cmd.status());
        Category saved = categories.saveAndFlush(category);
        changed(adminId, "category.update", "category", id);
        return saved;
    }

    private void validateParent(UUID categoryId, UUID parentId) {
        if (parentId != null && !categories.existsById(parentId)) {
            throw BusinessException.notFound("Category", parentId);
        }
        Map<UUID, UUID> parentOf = new HashMap<>();
        categories.findAll().forEach(c -> parentOf.put(c.getId(), c.getParentId()));
        new CategoryHierarchy(parentOf).validateMove(categoryId, parentId, props.maxCategoryDepth());
    }

    // ---------------------------------------------------------------- xizmatlar

    @Transactional(readOnly = true)
    public List<ServiceDefinition> services(UUID categoryId) {
        return categoryId == null
                ? services.findAllByOrderBySortOrderAscNameUzAsc()
                : services.findByCategoryIdOrderBySortOrderAscNameUzAsc(categoryId);
    }

    @Transactional
    public ServiceDefinition createService(UUID adminId, ServiceCommand cmd) {
        requireCategory(cmd.categoryId());
        if (services.existsBySlug(cmd.slug())) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        ServiceDefinition saved = services.saveAndFlush(new ServiceDefinition(cmd.categoryId(), cmd.slug(), cmd.name(),
                cmd.description(), cmd.sortOrder(), cmd.status()));
        changed(adminId, "service.create", "service", saved.getId());
        return saved;
    }

    @Transactional
    public ServiceDefinition updateService(UUID adminId, UUID id, ServiceCommand cmd) {
        ServiceDefinition service = services.findById(id).orElseThrow(() -> BusinessException.notFound("Service", id));
        requireCategory(cmd.categoryId());
        if (services.existsBySlugAndIdNot(cmd.slug(), id)) {
            throw new BusinessException(CatalogErrorCode.SLUG_TAKEN, cmd.slug());
        }
        service.update(cmd.categoryId(), cmd.slug(), cmd.name(), cmd.description(), cmd.sortOrder(), cmd.status());
        ServiceDefinition saved = services.saveAndFlush(service);
        changed(adminId, "service.update", "service", id);
        return saved;
    }

    private void requireCategory(UUID categoryId) {
        if (!categories.existsById(categoryId)) {
            throw BusinessException.notFound("Category", categoryId);
        }
    }

    private void changed(UUID adminId, String action, String entity, UUID id) {
        audit.record(adminId, "catalog." + action, entity, id, Map.of());
        outbox.publish("catalog", id, CatalogEvents.CATALOG_CHANGED, new CatalogEvents.CatalogChanged(entity, id));
    }
}
