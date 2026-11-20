package uz.usta.platform.catalog.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID> {

    List<ServiceDefinition> findByCategoryIdAndStatusOrderBySortOrderAscNameUzAsc(UUID categoryId, CatalogStatus status);

    List<ServiceDefinition> findByCategoryIdOrderBySortOrderAscNameUzAsc(UUID categoryId);

    List<ServiceDefinition> findAllByOrderBySortOrderAscNameUzAsc();

    List<ServiceDefinition> findByCategoryIdInAndStatus(Collection<UUID> categoryIds, CatalogStatus status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** Faol xizmatlarni nomi bo'yicha qidirish (uz/ru/en). */
    @Query("""
            select s from ServiceDefinition s
            where s.status = uz.usta.platform.catalog.internal.domain.CatalogStatus.ACTIVE
              and (lower(s.nameUz) like :q escape '\\' or lower(s.nameRu) like :q escape '\\'
                   or lower(s.nameEn) like :q escape '\\')
            order by s.sortOrder, s.nameUz""")
    List<ServiceDefinition> searchActive(@Param("q") String likePattern);
}
