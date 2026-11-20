package uz.usta.platform.order.internal.application;

import java.util.UUID;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.shared.error.BusinessException;

/** Nizo ochish qoidasi (mijoz va usta uchun bir xil). To'liq shikoyat oqimi — P4 (complaint moduli). */
final class Disputes {

    private Disputes() {
    }

    static Order open(Order order, UUID actorId, String reason, OrderTransitions transitions) {
        if (!OrderStatus.DISPUTABLE.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, order.getStatus().name(),
                    OrderStatus.DISPUTED.name());
        }
        transitions.change(order, OrderStatus.DISPUTED, actorId, reason);
        return order;
    }
}
