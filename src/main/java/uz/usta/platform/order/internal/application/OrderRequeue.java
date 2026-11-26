package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderOffer;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;

/**
 * Biriktirilgan usta ishni boshlamasdan chiqib ketdi (o'zi voz kechdi yoki bloklandi): buyurtma bekor
 * bo'lmaydi, yangi muddat bilan qayta qidiruvga o'tadi (ACCEPTED -> SEARCHING). O'sha usta qayta taklif olmaydi.
 */
@Component
class OrderRequeue {

    private final OrderOfferRepository offers;
    private final OrderTransitions transitions;
    private final OfferDispatcher dispatcher;
    private final OrderProperties props;
    private final Clock clock;

    OrderRequeue(OrderOfferRepository offers, OrderTransitions transitions, OfferDispatcher dispatcher,
                 OrderProperties props, Clock clock) {
        this.offers = offers;
        this.transitions = transitions;
        this.dispatcher = dispatcher;
        this.props = props;
        this.clock = clock;
    }

    /**
     * @param order   qulflangan, ACCEPTED holatdagi buyurtma
     * @param actorId {@code null} — tizim
     * @return chiqib ketgan ustaning (endi REJECTED) taklifi
     */
    @Transactional(propagation = Propagation.MANDATORY)
    OrderOffer requeue(Order order, UUID actorId, String reason) {
        Instant now = Instant.now(clock);
        UUID masterId = order.getMasterId();
        OrderOffer offer = offers.findByOrderIdAndMasterId(order.getId(), masterId)
                .orElseThrow(() -> new IllegalStateException("Accepted order " + order.getId() + " has no offer"));
        offer.respond(OfferStatus.REJECTED, now);
        offers.saveAndFlush(offer);
        order.releaseMaster(props.searchDeadline(now, order.getScheduledAt()));
        transitions.change(order, OrderStatus.SEARCHING, actorId, reason);
        dispatcher.dispatch(order);
        return offer;
    }
}
