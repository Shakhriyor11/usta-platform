package uz.usta.platform.catalog.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.catalog.internal.domain.MasterServicePrice;

public interface MasterServicePriceRepository extends JpaRepository<MasterServicePrice, UUID> {

    List<MasterServicePrice> findByMasterIdOrderByCreatedAtAsc(UUID masterId);

    List<MasterServicePrice> findByMasterIdAndActiveTrueOrderByPriceFromAsc(UUID masterId);

    Optional<MasterServicePrice> findByMasterIdAndServiceId(UUID masterId, UUID serviceId);

    long countByMasterId(UUID masterId);

    List<MasterServicePrice> findByServiceIdIn(Collection<UUID> serviceIds);

    @Query("select p.masterId from MasterServicePrice p where p.serviceId = :serviceId")
    List<UUID> findMasterIdsByServiceId(@Param("serviceId") UUID serviceId);

    @Query("""
            select distinct p.masterId from MasterServicePrice p
            where p.serviceId in (select s.id from ServiceDefinition s where s.categoryId in :categoryIds)""")
    List<UUID> findMasterIdsByCategoryIds(@Param("categoryIds") Collection<UUID> categoryIds);
}
