package uz.usta.platform.order.internal.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.OrderEvents;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.domain.OrderStatusHistory;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.order.internal.persistence.OrderStatusHistoryRepository;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/**
 * Yagona status o'zgartirish nuqtasi: ruxsat tekshiruvi (entity) -> {@code order_status_history} -> outbox event.
 * Hammasi chaqiruvchining tranzaksiyasida — yoki hammasi yoziladi, yoki hech biri.
 */
@Component
class OrderTransitions {

    private static final Logger log = LoggerFactory.getLogger(OrderTransitions.class);

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OutboxPublisher outbox;
    private final Clock clock;
    private final MeterRegistry metrics;

    OrderTransitions(OrderRepository orders, OrderStatusHistoryRepository history, OutboxPublisher outbox,
                     Clock clock, MeterRegistry metrics) {
        this.orders = orders;
        this.history = history;
        this.outbox = outbox;
        this.clock = clock;
        this.metrics = metrics;
    }

    /** Yangi buyurtmani saqlaydi va "yaratildi" yozuvini qo'shadi. */
    @Transactional(propagation = Propagation.MANDATORY)
    Order created(Order order, UUID actorId) {
        Order saved = orders.save(order);
        record(saved, null, actorId, null, Instant.now(clock));
        return saved;
    }

    /** @param actorId {@code null} — tizim */
    @Transactional(propagation = Propagation.MANDATORY)
    void change(Order order, OrderStatus next, UUID actorId, String reason) {
        Instant now = Instant.now(clock);
        OrderStatus previous = order.moveTo(next, now);
        orders.save(order);
        record(order, previous, actorId, reason, now);
        log.info("Order {}: {} -> {} (by {})", order.getId(), previous, next, actorId == null ? "system" : actorId);
    }

    private void record(Order order, OrderStatus previous, UUID actorId, String reason, Instant now) {
        // biznes metrikasi: Grafana'da buyurtmalar oqimi va "EXPIRED ulushi" alerti
        metrics.counter("orders.transitions", "to", order.getStatus().name()).increment();
        history.save(new OrderStatusHistory(order.getId(), previous, order.getStatus(), actorId, reason, now));
        outbox.publish(OrderEvents.AGGREGATE, order.getId(), OrderEvents.STATUS_CHANGED,
                new OrderEvents.StatusChanged(order.getId(), order.getCustomerId(), order.getMasterId(),
                        previous == null ? null : previous.name(), order.getStatus().name(), actorId, reason));
    }
}
