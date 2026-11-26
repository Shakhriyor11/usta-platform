package uz.usta.platform.order.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.OrderOffer;

public interface OrderOfferRepository extends JpaRepository<OrderOffer, UUID> {

    Optional<OrderOffer> findByOrderIdAndMasterId(UUID orderId, UUID masterId);

    List<OrderOffer> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, OfferStatus status);

    /** Ustada javob kutayotgan takliflari bor buyurtmalar. */
    @Query("""
            select f.orderId from OrderOffer f
            where f.masterId = :masterId and f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING""")
    List<UUID> findPendingOrderIds(@Param("masterId") UUID masterId);

    /** Ustaning hali javob berilmagan va muddati tugamagan takliflari. */
    @Query("""
            select f from OrderOffer f
            where f.masterId = :masterId and f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING
              and f.expiresAt > :now
            order by f.createdAt desc""")
    List<OrderOffer> findOpenForMaster(@Param("masterId") UUID masterId, @Param("now") Instant now);

    /** Buyurtmaning ochiq takliflarini yopadi (boshqa usta qabul qildi, bekor qilindi, muddati tugadi). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update OrderOffer f set f.status = :result, f.respondedAt = :now
            where f.orderId = :orderId and f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING""")
    int closePending(@Param("orderId") UUID orderId, @Param("result") OfferStatus result, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update OrderOffer f set f.status = uz.usta.platform.order.internal.domain.OfferStatus.EXPIRED,
                                    f.respondedAt = :now
            where f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING and f.expiresAt <= :now""")
    int expireDue(@Param("now") Instant now);
}
