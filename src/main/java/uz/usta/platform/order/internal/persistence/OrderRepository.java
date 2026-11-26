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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
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

    /**
     * Qidirilayotgan, lekin hozir hech bir ustada ochiq taklif yo'q buyurtmalar — qayta taqsimlash uchun.
     * Eng uzoq kutganidan: ustasi topilmayotgan uzoq muddatli buyurtmalar yangilarini to'sib qo'ymaydi.
     */
    @Query("""
            select o.id from Order o
            where o.status = uz.usta.platform.order.internal.domain.OrderStatus.SEARCHING and o.searchDeadline > :now
              and not exists (select 1 from OrderOffer f where f.orderId = o.id
                              and f.status = uz.usta.platform.order.internal.domain.OfferStatus.PENDING)
            order by o.dispatchAttemptedAt asc nulls first, o.createdAt""")
    List<UUID> findSearchingWithoutOpenOffers(@Param("now") Instant now, Pageable limit);

    /** Hozir yoki {@code horizon} gacha ishi bor ustalar. */
    @Query("""
            select distinct o.masterId from Order o
            where o.status = uz.usta.platform.order.internal.domain.OrderStatus.IN_PROGRESS
               or (o.status = uz.usta.platform.order.internal.domain.OrderStatus.ACCEPTED
                   and (o.scheduledAt is null or o.scheduledAt <= :horizon))""")
    List<UUID> findBusyMasterIds(@Param("horizon") Instant horizon);

    @Query("select o.id from Order o where o.customerId = :userId and o.status in :statuses")
    List<UUID> findIdsByCustomer(@Param("userId") UUID userId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select o.id from Order o where o.masterId = :userId and o.status in :statuses")
    List<UUID> findIdsByMaster(@Param("userId") UUID userId, @Param("statuses") Collection<OrderStatus> statuses);

    /** O'chirilgan mijoz buyurtmalaridagi shaxsiy ma'lumotlar ({@code location} koordinatadan qayta hisoblanadi). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @NativeQuery("""
            update orders
               set address_title = null, address_details = null, address_text = '-', description = '-',
                   latitude = round(cast(latitude as numeric), 2), longitude = round(cast(longitude as numeric), 2),
                   updated_at = now(), version = version + 1
             where customer_id = :customerId""")
    int anonymizeCustomer(@Param("customerId") UUID customerId);
}
