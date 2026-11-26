package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.search.MasterMatcher;
import uz.usta.platform.search.MasterMatcher.Candidate;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/**
 * Buyurtmani ustalarga taqsimlash: {@code offerBatchSize} ta mos ustaga (avval online, keyin eng yaqinlari) bir
 * vaqtda taklif yuboriladi, birinchi qabul qilgan oladi. Mijoz ustani o'zi tanlagan bo'lsa — birinchi taklif faqat
 * unga. Komissiya qarzi muddati o'tgan ustalarga yuborilmaydi; yaqin vaqtda boshlanadigan buyurtmalarga band
 * ustalar ham chetlab o'tiladi. Har usta bitta buyurtma uchun bir martagina taklif oladi (istisno: biriktirilgan
 * usta voz kechsa, oldin shunchaki yopilgan takliflar qayta ochiladi). Ochiq taklif qolmasa (hammasi rad
 * etilgan/muddati o'tgan) keyingi to'plam yuboriladi — {@code OrderMaintenance} yoki rad etish paytida.
 */
@Component
class OfferDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OfferDispatcher.class);

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final MasterMatcher matcher;
    private final Commissions commissions;
    private final OutboxPublisher outbox;
    private final OrderProperties props;
    private final Clock clock;

    OfferDispatcher(OrderRepository orders, OrderOfferRepository offers, MasterMatcher matcher,
                    Commissions commissions, OutboxPublisher outbox, OrderProperties props, Clock clock) {
        this.orders = orders;
        this.offers = offers;
        this.matcher = matcher;
        this.commissions = commissions;
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
        order.markDispatchAttempt(now);
        Map<UUID, OrderOffer> previous = offers.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .collect(Collectors.toMap(OrderOffer::getMasterId, Function.identity()));
        Set<UUID> exclude = previous.values().stream().filter(o -> o.getStatus() != OfferStatus.CANCELLED)
                .map(OrderOffer::getMasterId).collect(Collectors.toCollection(HashSet::new));
        exclude.add(order.getCustomerId()); // usta ham, mijoz ham bo'lgan foydalanuvchi o'ziga buyurtma olmaydi
        exclude.addAll(commissions.overdueDebtors());
        List<Candidate> candidates = new ArrayList<>();
        UUID preferred = order.getPreferredMasterId();
        if (preferred != null && previous.isEmpty() && !exclude.contains(preferred)) {
            matcher.match(preferred, order.getServiceId(), order.getLatitude(), order.getLongitude())
                    .ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            if (props.startsSoon(now, order.getScheduledAt())) {
                exclude.addAll(orders.findBusyMasterIds(now.plus(props.busyHorizon())));
            }
            candidates = matcher.nearest(order.getServiceId(), order.getLatitude(), order.getLongitude(),
                    props.offerBatchSize(), exclude);
        }
        Instant expiresAt = min(now.plus(props.offerTtl()), order.getSearchDeadline());
        for (Candidate c : candidates) {
            OrderOffer existing = previous.get(c.masterId());
            if (existing == null) {
                offers.save(new OrderOffer(order.getId(), c.masterId(), c.distanceMeters(), c.priceFrom(), expiresAt));
            } else {
                existing.reopen(c.distanceMeters(), c.priceFrom(), expiresAt);
                offers.save(existing);
            }
            outbox.publish(OrderEvents.AGGREGATE, order.getId(), OrderEvents.OFFER_CREATED,
                    new OrderEvents.OfferCreated(order.getId(), c.masterId(), order.getServiceId(), expiresAt,
                            c.distanceMeters()));
        }
        if (candidates.isEmpty()) {
            log.debug("Order {}: no matching masters yet — will retry", order.getId());
        } else {
            log.info("Order {}: {} offers sent", order.getId(), candidates.size());
        }
        return candidates.size();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
