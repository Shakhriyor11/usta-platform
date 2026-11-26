package uz.usta.platform.order.internal.application;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.usta.platform.identity.UserDeletionGuard;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.shared.error.BusinessException;

/**
 * Boshlangan yoki nizodagi ishi bor hisob o'chirilmaydi — avval yakunlanishi/hal qilinishi kerak. Boshlanmagan
 * buyurtmalar o'chirilgandan keyin {@code BlockedUserListener} orqali bekor qilinadi yoki qayta qidiriladi.
 */
@Component
class OrderDeletionGuard implements UserDeletionGuard {

    private static final Set<OrderStatus> UNFINISHED =
            EnumSet.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.DISPUTED);

    private final OrderRepository orders;

    OrderDeletionGuard(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public void requireDeletable(UUID userId) {
        if (!orders.findIdsByCustomer(userId, UNFINISHED).isEmpty()
                || !orders.findIdsByMaster(userId, UNFINISHED).isEmpty()) {
            throw new BusinessException(OrderErrorCode.USER_HAS_ACTIVE_ORDERS);
        }
    }
}
