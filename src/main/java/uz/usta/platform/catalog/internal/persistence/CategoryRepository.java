package uz.usta.platform.catalog.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAscNameUzAsc();

    List<Category> findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
