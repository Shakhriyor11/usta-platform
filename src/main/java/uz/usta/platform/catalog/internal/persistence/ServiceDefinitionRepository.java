package uz.usta.platform.catalog.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID> {

    List<ServiceDefinition> findByCategoryIdAndStatusOrderBySortOrderAscNameUzAsc(UUID categoryId, CatalogStatus status);

    List<ServiceDefinition> findByCategoryIdAndDeletedAtIsNullOrderBySortOrderAscNameUzAsc(UUID categoryId);

    List<ServiceDefinition> findByDeletedAtIsNullOrderBySortOrderAscNameUzAsc();

    Optional<ServiceDefinition> findByIdAndDeletedAtIsNull(UUID id);

    List<ServiceDefinition> findByCategoryIdInAndDeletedAtIsNull(Collection<UUID> categoryIds);

    List<ServiceDefinition> findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus status);

    List<ServiceDefinition> findByCategoryIdInAndStatus(Collection<UUID> categoryIds, CatalogStatus status);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);
}
