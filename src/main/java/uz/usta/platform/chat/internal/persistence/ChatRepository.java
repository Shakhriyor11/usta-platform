package uz.usta.platform.chat.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.chat.internal.domain.Chat;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByOrderId(UUID orderId);

    /** Parallel birinchi xabarlar ikkita chat yaratmasligi uchun (UNIQUE order_id). */
    @Modifying
    @NativeQuery("""
            insert into chats (id, order_id, customer_id, master_id) values (:id, :orderId, :customerId, :masterId)
            on conflict (order_id) do nothing""")
    int insertIfAbsent(@Param("id") UUID id, @Param("orderId") UUID orderId, @Param("customerId") UUID customerId,
                       @Param("masterId") UUID masterId);
}
