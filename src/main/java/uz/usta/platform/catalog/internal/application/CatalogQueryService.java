package uz.usta.platform.catalog.internal.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.i18n.LocalizedText;

/** Mijozlar uchun katalog: faqat faol elementlar; nofaol kategoriyaning butun shoxi yashiriladi. */
@Service
public class CatalogQueryService {

    private static final Pattern APOSTROPHES = Pattern.compile("[ʻʼ‘’`]");

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;

    private final ServiceAvailability availability;

    CatalogQueryService(CategoryRepository categories, ServiceDefinitionRepository services,
                        ServiceAvailability availability) {
        this.categories = categories;
        this.services = services;
        this.availability = availability;
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
        Category category = categories.findById(categoryId).filter(c -> categories.isBranchActive(c.getId()))
                .orElseThrow(() -> BusinessException.notFound("Category", categoryId));
        return services.findByCategoryIdAndStatusOrderBySortOrderAscNameUzAsc(category.getId(), CatalogStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public ServiceDefinition activeService(UUID serviceId) {
        return services.findById(serviceId).filter(availability::isAvailable)
                .orElseThrow(() -> BusinessException.notFound("Service", serviceId));
    }

    /**
     * Nom bo'yicha qidirish (uz lotin va kirill, ru, en). Katalog kichik bo'lgani uchun xotirada solishtiriladi —
     * kiritilmagan kirillcha nom ham (o'girilgan holda) topiladi.
     */
    @Transactional(readOnly = true)
    public List<ServiceDefinition> search(String query) {
        String needle = normalize(query);
        return availability.available(services.findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus.ACTIVE).stream()
                .filter(s -> matches(s.getName(), needle))
                .toList());
    }

    private static boolean matches(LocalizedText name, String needle) {
        return Stream.of(name.uz(), name.ru(), name.en(), name.cyrillic())
                .anyMatch(v -> v != null && normalize(v).contains(needle));
    }

    /** Katta-kichik harf va o'zbekcha tutuq belgisining turli ko'rinishlari ({@code ʻ ʼ ‘ ’ `}) farqlanmaydi. */
    private static String normalize(String text) {
        return APOSTROPHES.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll("'");
    }
}
