package uz.usta.platform.catalog.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;

/**
 * Xizmat mijozlarga mavjudmi: o'zi faol va kategoriyasi ham, uning barcha ota-bobolari ham faol bo'lishi kerak
 * (nofaol kategoriyaning butun shoxi yashiriladi — qidiruv va buyurtmada ham).
 */
@Component
class ServiceAvailability {

    private final CategoryRepository categories;

    ServiceAvailability(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(ServiceDefinition service) {
        return service.isActive() && categories.isBranchActive(service.getCategoryId());
    }

    /** Berilganlardan mavjudlari (tartib saqlanadi); har bir kategoriya bir marta tekshiriladi. */
    @Transactional(readOnly = true)
    public List<ServiceDefinition> available(Collection<ServiceDefinition> services) {
        List<UUID> hidden = services.stream().map(ServiceDefinition::getCategoryId).distinct()
                .filter(id -> !categories.isBranchActive(id)).toList();
        return services.stream().filter(s -> s.isActive() && !hidden.contains(s.getCategoryId())).toList();
    }
}
