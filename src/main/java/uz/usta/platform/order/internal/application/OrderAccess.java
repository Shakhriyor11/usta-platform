package uz.usta.platform.order.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.shared.error.BusinessException;

/** Buyurtmani yuklash va egalikni tekshirish. Begona buyurtma — 404 (mavjudligini oshkor qilmaymiz). */
@Component
class OrderAccess {

    private final OrderRepository orders;

    OrderAccess(OrderRepository orders) {
        this.orders = orders;
    }

    Order lock(UUID orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
    }

    Order lockForCustomer(UUID orderId, UUID customerId) {
        Order order = lock(orderId);
        if (!order.isOwnedBy(customerId)) {
            throw notFound(orderId);
        }
        return order;
    }

    Order lockForMaster(UUID orderId, UUID masterId) {
        Order order = lock(orderId);
        if (!order.isAssignedTo(masterId)) {
            throw notFound(orderId);
        }
        return order;
    }

    Order get(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> notFound(orderId));
    }

    static BusinessException notFound(UUID orderId) {
        return BusinessException.notFound("Order", orderId);
    }
}
