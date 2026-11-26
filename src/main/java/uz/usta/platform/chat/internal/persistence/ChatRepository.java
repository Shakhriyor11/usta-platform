package uz.usta.platform.chat.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.chat.internal.domain.Chat;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    /** Buyurtmaning shu usta bilan chati (usta almashsa, yangisi bilan alohida chat). */
    Optional<Chat> findByOrderIdAndMasterId(UUID orderId, UUID masterId);

    /** Admin uchun: buyurtmaning barcha chatlari. */
    @Query("select c.id from Chat c where c.orderId = :orderId")
    List<UUID> findIdsByOrderId(@Param("orderId") UUID orderId);

    /** Parallel birinchi xabarlar ikkita chat yaratmasligi uchun (UNIQUE order_id, master_id). */
    @Modifying
    @NativeQuery("""
            insert into chats (id, order_id, customer_id, master_id) values (:id, :orderId, :customerId, :masterId)
            on conflict (order_id, master_id) do nothing""")
    int insertIfAbsent(@Param("id") UUID id, @Param("orderId") UUID orderId, @Param("customerId") UUID customerId,
                       @Param("masterId") UUID masterId);
}
