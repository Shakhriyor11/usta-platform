package uz.usta.platform.order.internal.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.shared.error.BusinessException;

@Component
class OrderApiFacade implements OrderApi {

    private final OrderAccess access;
    private final OrderTransitions transitions;
    private final AdminOrderService admin;
    private final OrderRepository orders;
    private final Commissions commissions;

    OrderApiFacade(OrderAccess access, OrderTransitions transitions, AdminOrderService admin, OrderRepository orders,
                   Commissions commissions) {
        this.access = access;
        this.transitions = transitions;
        this.admin = admin;
        this.orders = orders;
        this.commissions = commissions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReviewTarget lockForReview(UUID orderId, UUID customerId) {
        Order order = access.lockForCustomer(orderId, customerId);
        if (order.getStatus() != OrderStatus.COMMISSION && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_REVIEWABLE, order.getStatus().name());
        }
        return new ReviewTarget(orderId, customerId, order.getMasterId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markReviewed(UUID orderId, UUID customerId) {
        transitions.change(access.lockForCustomer(orderId, customerId), OrderStatus.REVIEWED, customerId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Participants participants(UUID orderId) {
        Order o = access.get(orderId);
        return new Participants(o.getId(), o.getCustomerId(), o.getMasterId(), o.getStatus().name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openDispute(UUID orderId, UUID actorId, String reason) {
        Order order = access.lock(orderId);
        if (!order.isOwnedBy(actorId) && !order.isAssignedTo(actorId)) {
            throw OrderAccess.notFound(orderId);
        }
        Disputes.open(order, actorId, reason, transitions, commissions);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveDispute(UUID adminId, UUID orderId, DisputeResolution resolution, BigDecimal price,
                               String note) {
        admin.resolveDispute(adminId, orderId, AdminOrderService.Resolution.valueOf(resolution.name()), price, note);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommissionedOrder> completedBetween(Instant from, Instant to) {
        return orders.findCompletedBetween(from, to).stream()
                .map(o -> new CommissionedOrder(o.getId(), o.getMasterId(), o.getStatus().name(), o.getPrice(),
                        o.getCommissionAmount(), o.getCompletedAt()))
                .toList();
    }
}
