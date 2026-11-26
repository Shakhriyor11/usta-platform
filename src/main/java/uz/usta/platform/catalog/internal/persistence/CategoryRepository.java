package uz.usta.platform.catalog.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByDeletedAtIsNullOrderBySortOrderAscNameUzAsc();

    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<Category> findByStatusOrderBySortOrderAscNameUzAsc(CatalogStatus status);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    /** Kategoriya va uning barcha ota-bobolari faolmi. */
    @NativeQuery("""
            with recursive chain as (
                select id, parent_id, status from categories where id = :id
                union all
                select c.id, c.parent_id, c.status from categories c join chain on c.id = chain.parent_id
            )
            select not exists (select 1 from chain where status <> 'ACTIVE')""")
    boolean isBranchActive(@Param("id") UUID id);
}
