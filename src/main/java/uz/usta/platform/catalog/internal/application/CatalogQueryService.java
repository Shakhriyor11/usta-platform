package uz.usta.platform.catalog.internal.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.error.BusinessException;

/** Mijozlar uchun katalog: faqat faol elementlar; nofaol kategoriyaning butun shoxi yashiriladi. */
@Service
public class CatalogQueryService {

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;

    CatalogQueryService(CategoryRepository categories, ServiceDefinitionRepository services) {
        this.categories = categories;
        this.services = services;
    }

    public record CategoryNode(Category category, List<CategoryNode> children) {
    }

    @Transactional(readOnly = true)
    public List<CategoryNode> activeTree() {
        List<Category> active = categories.findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus.ACTIVE);
        Map<UUID, List<Category>> byParent = new HashMap<>();
        for (Category c : active) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        // ildizdan pastga yuramiz: ota-onasi nofaol bo'lgan kategoriya daraxtga tushmaydi
        return build(byParent, null);
    }

    private List<CategoryNode> build(Map<UUID, List<Category>> byParent, UUID parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c, build(byParent, c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceDefinition> activeServices(UUID categoryId) {
        Category category = categories.findById(categoryId).filter(Category::isActive)
                .orElseThrow(() -> BusinessException.notFound("Category", categoryId));
        return services.findByCategoryIdAndStatusOrderBySortOrderAscNameUzAsc(category.getId(), CatalogStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public ServiceDefinition activeService(UUID serviceId) {
        return services.findById(serviceId).filter(ServiceDefinition::isActive)
                .orElseThrow(() -> BusinessException.notFound("Service", serviceId));
    }

    @Transactional(readOnly = true)
    public List<ServiceDefinition> search(String query) {
        String escaped = query.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return services.searchActive("%" + escaped + "%");
    }
}
