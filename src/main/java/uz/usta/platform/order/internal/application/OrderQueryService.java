package uz.usta.platform.order.internal.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.internal.domain.OrderAttachment;
import uz.usta.platform.order.internal.domain.OrderOffer;
import uz.usta.platform.order.internal.domain.OrderStatusHistory;
import uz.usta.platform.order.internal.persistence.OrderAttachmentRepository;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderStatusHistoryRepository;

/** Buyurtma tafsilotlari uchun bog'liq ma'lumotlar (tarix, biriktirmalar, takliflar). */
@Service
public class OrderQueryService {

    private final OrderStatusHistoryRepository history;
    private final OrderAttachmentRepository attachments;
    private final OrderOfferRepository offers;

    OrderQueryService(OrderStatusHistoryRepository history, OrderAttachmentRepository attachments,
                      OrderOfferRepository offers) {
        this.history = history;
        this.attachments = attachments;
        this.offers = offers;
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> history(UUID orderId) {
        return history.findByOrderIdOrderByChangedAtAscIdAsc(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderAttachment> attachments(UUID orderId) {
        return attachments.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderOffer> offers(UUID orderId) {
        return offers.findByOrderIdOrderByCreatedAtAsc(orderId);
    }
}
