package uz.usta.platform.catalog.internal.application;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.catalog.internal.domain.CatalogErrorCode;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.MasterServicePrice;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.MasterServicePriceRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.i18n.LocalizedText;

@Component
class CatalogApiFacade implements CatalogApi {

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;
    private final MasterServicePriceRepository prices;
    private final ServiceAvailability availability;

    CatalogApiFacade(CategoryRepository categories, ServiceDefinitionRepository services,
                     MasterServicePriceRepository prices, ServiceAvailability availability) {
        this.categories = categories;
        this.services = services;
        this.prices = prices;
        this.availability = availability;
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceInfo requireActiveService(UUID serviceId) {
        return services.findById(serviceId).filter(availability::isAvailable)
                .map(s -> new ServiceInfo(s.getId(), s.getCategoryId(), s.getName()))
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.SERVICE_NOT_AVAILABLE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeServiceIdsInCategory(UUID categoryId) {
        List<Category> active = categories.findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus.ACTIVE);
        Map<UUID, Category> byId = active.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        // kategoriya va barcha ota-bobolari faol bo'lishi kerak
        Category start = byId.get(categoryId);
        for (Category cur = start; cur != null && cur.getParentId() != null; cur = byId.get(cur.getParentId())) {
            if (!byId.containsKey(cur.getParentId())) {
                start = null;
            }
        }
        if (start == null) {
            throw BusinessException.notFound("Category", categoryId);
        }
        Map<UUID, List<UUID>> children = new HashMap<>();
        active.forEach(c -> children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId()));
        List<UUID> subtree = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(categoryId));
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            subtree.add(id);
            queue.addAll(children.getOrDefault(id, List.of()));
        }
        return services.findByCategoryIdInAndStatus(subtree, CatalogStatus.ACTIVE).stream()
                .map(ServiceDefinition::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LocalizedText> serviceNames(Collection<UUID> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        return services.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(ServiceDefinition::getId, ServiceDefinition::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Offering> activeOfferings(UUID masterId) {
        List<MasterServicePrice> list = prices.findByMasterIdAndActiveTrueOrderByPriceFromAsc(masterId);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, ServiceDefinition> defs = availability.available(services.findAllById(list.stream()
                .map(MasterServicePrice::getServiceId).toList())).stream()
                .collect(Collectors.toMap(ServiceDefinition::getId, Function.identity()));
        return list.stream()
                .filter(p -> defs.containsKey(p.getServiceId()))
                .map(p -> new Offering(p.getServiceId(), p.getPriceFrom()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> priceFrom(UUID masterId, UUID serviceId) {
        return prices.findByMasterIdAndServiceId(masterId, serviceId).filter(MasterServicePrice::isActive)
                .map(MasterServicePrice::getPriceFrom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> mastersOffering(UUID serviceId) {
        return prices.findMasterIdsByServiceId(serviceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> mastersOfferingInCategory(UUID categoryId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        categories.findAll().forEach(c -> children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                .add(c.getId()));
        List<UUID> subtree = new ArrayList<>(List.of(categoryId));
        for (int i = 0; i < subtree.size(); i++) {
            subtree.addAll(children.getOrDefault(subtree.get(i), List.of()));
        }
        return prices.findMasterIdsByCategoryIds(subtree);
    }
}
