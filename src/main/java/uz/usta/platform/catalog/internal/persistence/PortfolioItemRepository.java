package uz.usta.platform.catalog.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.catalog.internal.domain.PortfolioItem;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, UUID> {

    List<PortfolioItem> findByMasterIdOrderByCreatedAtDesc(UUID masterId);

    Optional<PortfolioItem> findByIdAndMasterId(UUID id, UUID masterId);

    long countByMasterId(UUID masterId);
}
