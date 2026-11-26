package uz.usta.platform.order.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.order.internal.domain.OrderAttachment;

public interface OrderAttachmentRepository extends JpaRepository<OrderAttachment, UUID> {

    List<OrderAttachment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<OrderAttachment> findByIdAndOrderId(UUID id, UUID orderId);

    long countByOrderId(UUID orderId);

    @Query("""
            select a from OrderAttachment a
            where a.orderId in (select o.id from Order o where o.customerId = :customerId)""")
    List<OrderAttachment> findByCustomer(@Param("customerId") UUID customerId);
}
