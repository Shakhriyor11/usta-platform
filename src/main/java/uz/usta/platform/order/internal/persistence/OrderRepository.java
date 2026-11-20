package uz.usta.platform.order.internal.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * {@code SELECT ... FOR UPDATE}: bir buyurtmani ikki usta bir vaqtda qabul qilmasligi va status o'tishlari
     * ketma-ket bo'lishi uchun. Qo'shimcha himoya — {@code @Version} (optimistic) va uq_offers_accepted indeksi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatusIn(UUID customerId, Collection<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByMasterId(UUID masterId, Pageable pageable);

    Page<Order> findByMasterIdAndStatusIn(UUID masterId, Collection<OrderStatus> statuses, Pageable pageable);

    @Query("select o from Order o where o.completedAt >= :from and o.completedAt < :to order by o.completedAt")
    List<Order> findCompletedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Qidiruv muddati tugagan buyurtmalar. */
    @Query("""
            select o.id from Order o
            where o.status = uz.usta.platform.order.internal.domain.OrderStatus.SEARCHING and o.searchDeadline <= :now
            order by o.searchDeadline""")
    List<UUID> findExpiredSearching(@Param("now") Instant now, Pageable limit);

    /** Qidirilayotgan, lekin hozir hech bir ustada ochiq taklif yo'q buyurtmalar — qayta taqsimlash uchun. */
    @Query("""
            select o.id from Order o
            where o.status = uz.usta.platform.order.internal.domain.OrderStatus.SEARCHING and o.searchDeadline > :now
              and not exists (select 1 from OrderOffer f where f.orderId = o.id
                              and f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING)
            order by o.createdAt""")
    List<UUID> findSearchingWithoutOpenOffers(@Param("now") Instant now, Pageable limit);
}
