package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.shared.outbox.OutboxMessage;

/**
 * Foydalanuvchi bloklanganda yoki o'chirilganda uning buyurtmalari "osilib" qolmaydi:
 * <ul>
 *   <li>mijoz — ish boshlanmagan buyurtmalari bekor qilinadi;</li>
 *   <li>usta — qabul qilgan buyurtmalari qayta qidiruvga o'tadi, boshlangan ishlari nizoga (admin hal qiladi),
 *       javob kutayotgan takliflari yopilib keyingi ustalarga yuboriladi.</li>
 * </ul>
 * Idempotent: faqat hali mos holatdagi buyurtmalarga ta'sir qiladi.
 */
@Component
class BlockedUserListener {

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final OrderAccess access;
    private final OrderTransitions transitions;
    private final OfferDispatcher dispatcher;
    private final OrderRequeue requeue;
    private final Commissions commissions;
    private final JsonMapper json;
    private final Clock clock;

    BlockedUserListener(OrderRepository orders, OrderOfferRepository offers, OrderAccess access,
                        OrderTransitions transitions, OfferDispatcher dispatcher, OrderRequeue requeue,
                        Commissions commissions, JsonMapper json, Clock clock) {
        this.orders = orders;
        this.offers = offers;
        this.access = access;
        this.transitions = transitions;
        this.dispatcher = dispatcher;
        this.requeue = requeue;
        this.commissions = commissions;
        this.json = json;
        this.clock = clock;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_BLOCKED + "'")
    @Transactional
    public void onBlocked(OutboxMessage m) {
        release(json.readValue(m.payload(), IdentityEvents.UserBlocked.class).userId(),
                SystemReasons.CUSTOMER_BLOCKED, SystemReasons.MASTER_BLOCKED);
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        release(json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId(),
                SystemReasons.CUSTOMER_DELETED, SystemReasons.MASTER_DELETED);
    }

    private void release(UUID userId, String customerReason, String masterReason) {
        orders.findIdsByCustomer(userId, OrderStatus.CUSTOMER_CANCELLABLE)
                .forEach(id -> cancelForCustomer(id, customerReason));
        orders.findIdsByMaster(userId, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS))
                .forEach(id -> releaseMaster(id, userId, masterReason));
        offers.findPendingOrderIds(userId).forEach(id -> withdrawOffer(id, userId));
    }

    private void cancelForCustomer(UUID orderId, String reason) {
        Order order = access.lock(orderId);
        if (!OrderStatus.CUSTOMER_CANCELLABLE.contains(order.getStatus())) {
            return;
        }
        order.recordCancellation(null, reason);
        transitions.change(order, OrderStatus.CANCELLED, null, reason);
        offers.closePending(orderId, OfferStatus.CANCELLED, Instant.now(clock));
    }

    private void releaseMaster(UUID orderId, UUID masterId, String reason) {
        Order order = access.lock(orderId);
        if (!order.isAssignedTo(masterId)) {
            return;
        }
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            requeue.requeue(order, null, reason);
        } else if (order.getStatus() == OrderStatus.IN_PROGRESS) {
            Disputes.open(order, null, reason, transitions, commissions);
        }
    }

    private void withdrawOffer(UUID orderId, UUID masterId) {
        Order order = access.lock(orderId);
        offers.findByOrderIdAndMasterId(orderId, masterId)
                .filter(o -> o.getStatus() == OfferStatus.PENDING)
                .ifPresent(o -> {
                    o.respond(OfferStatus.REJECTED, Instant.now(clock));
                    offers.saveAndFlush(o);
                    dispatcher.dispatch(order);
                });
    }
}
