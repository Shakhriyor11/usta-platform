package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.OrderEvents;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderOffer;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.search.MasterMatcher;
import uz.usta.platform.search.MasterMatcher.Candidate;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/**
 * Buyurtmani ustalarga taqsimlash: eng yaqin {@code offerBatchSize} ta mos ustaga bir vaqtda taklif yuboriladi,
 * birinchi qabul qilgan oladi. Mijoz ustani o'zi tanlagan bo'lsa — birinchi taklif faqat unga. Har usta bitta
 * buyurtma uchun bir martagina taklif oladi. Ochiq taklif qolmasa (hammasi rad etilgan/muddati o'tgan)
 * keyingi to'plam yuboriladi — {@code OrderMaintenance} yoki rad etish paytida.
 */
@Component
class OfferDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OfferDispatcher.class);

    private final OrderOfferRepository offers;
    private final MasterMatcher matcher;
    private final OutboxPublisher outbox;
    private final OrderProperties props;
    private final Clock clock;

    OfferDispatcher(OrderOfferRepository offers, MasterMatcher matcher, OutboxPublisher outbox, OrderProperties props,
                    Clock clock) {
        this.offers = offers;
        this.matcher = matcher;
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Buyurtma qulflangan (FOR UPDATE) holda chaqiriladi.
     *
     * @return yuborilgan takliflar soni
     */
    @Transactional(propagation = Propagation.MANDATORY)
    int dispatch(Order order) {
        Instant now = Instant.now(clock);
        if (order.getStatus() != OrderStatus.SEARCHING || !order.getSearchDeadline().isAfter(now)
                || offers.existsByOrderIdAndStatus(order.getId(), OfferStatus.PENDING)) {
            return 0;
        }
        Set<UUID> exclude = new HashSet<>(offers.findMasterIdsByOrderId(order.getId()));
        List<Candidate> candidates = new ArrayList<>();
        if (order.getPreferredMasterId() != null && exclude.isEmpty()) {
            matcher.match(order.getPreferredMasterId(), order.getServiceId(), order.getLatitude(),
                    order.getLongitude()).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            exclude.add(order.getCustomerId()); // usta ham, mijoz ham bo'lgan foydalanuvchi o'ziga buyurtma olmaydi
            candidates = matcher.nearest(order.getServiceId(), order.getLatitude(), order.getLongitude(),
                    props.offerBatchSize(), exclude);
        }
        Instant expiresAt = min(now.plus(props.offerTtl()), order.getSearchDeadline());
        for (Candidate c : candidates) {
            offers.save(new OrderOffer(order.getId(), c.masterId(), c.distanceMeters(), expiresAt));
            outbox.publish(OrderEvents.AGGREGATE, order.getId(), OrderEvents.OFFER_CREATED,
                    new OrderEvents.OfferCreated(order.getId(), c.masterId(), order.getServiceId(), expiresAt,
                            c.distanceMeters()));
        }
        log.info("Order {}: {} offers sent{}", order.getId(), candidates.size(),
                candidates.isEmpty() ? " (no matching masters yet — will retry)" : "");
        return candidates.size();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
