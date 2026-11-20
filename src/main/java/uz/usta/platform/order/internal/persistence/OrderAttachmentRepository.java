package uz.usta.platform.order.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.order.internal.domain.OrderAttachment;

public interface OrderAttachmentRepository extends JpaRepository<OrderAttachment, UUID> {

    List<OrderAttachment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<OrderAttachment> findByIdAndOrderId(UUID id, UUID orderId);

    long countByOrderId(UUID orderId);
}
