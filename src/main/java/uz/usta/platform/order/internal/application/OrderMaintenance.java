package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;

/**
 * Fon vazifasi (klasterda bitta instansiyada — ShedLock):
 * <ol>
 *   <li>javob muddati o'tgan takliflar -> EXPIRED;</li>
 *   <li>qidiruv muddati ({@code searchTimeout}) o'tgan buyurtmalar -> EXPIRED;</li>
 *   <li>ochiq taklifi qolmagan SEARCHING buyurtmalar -> keyingi ustalarga taklif (retry).</li>
 * </ol>
 * Har bir buyurtma alohida tranzaksiyada va qulf bilan — bitta xato boshqalarini to'xtatmaydi.
 */
@Component
public class OrderMaintenance {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenance.class);

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final OrderTransitions transitions;
    private final OfferDispatcher dispatcher;
    private final OrderAccess access;
    private final TransactionTemplate tx;
    private final OrderProperties props;
    private final Clock clock;

    OrderMaintenance(OrderRepository orders, OrderOfferRepository offers, OrderTransitions transitions,
                     OfferDispatcher dispatcher, OrderAccess access, TransactionTemplate tx, OrderProperties props,
                     Clock clock) {
        this.orders = orders;
        this.offers = offers;
        this.transitions = transitions;
        this.dispatcher = dispatcher;
        this.access = access;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    public record Result(int offersExpired, int ordersExpired, int offersSent) {
    }

    @Scheduled(fixedDelayString = "${app.order.maintenance-interval:PT30S}", initialDelayString = "PT20S")
    @SchedulerLock(name = "order-maintenance", lockAtMostFor = "PT5M")
    public void scheduled() {
        Result r = run();
        if (r.offersExpired() + r.ordersExpired() + r.offersSent() > 0) {
            log.info("Order maintenance: {}", r);
        }
    }

    public Result run() {
        Instant now = Instant.now(clock);
        Integer expiredOffers = tx.execute(s -> offers.expireDue(now));
        int expiredOrders = 0;
        for (UUID id : orders.findExpiredSearching(now, PageRequest.ofSize(props.maintenanceBatch()))) {
            expiredOrders += safely(id, () -> expire(id, now));
        }
        int sent = 0;
        for (UUID id : orders.findSearchingWithoutOpenOffers(now, PageRequest.ofSize(props.maintenanceBatch()))) {
            sent += safely(id, () -> tx.execute(s -> dispatcher.dispatch(access.lock(id))));
        }
        return new Result(expiredOffers == null ? 0 : expiredOffers, expiredOrders, sent);
    }

    private int expire(UUID id, Instant now) {
        Integer n = tx.execute(s -> {
            Order order = access.lock(id);
            if (order.getStatus() != OrderStatus.SEARCHING || order.getSearchDeadline().isAfter(now)) {
                return 0;
            }
            transitions.change(order, OrderStatus.EXPIRED, null, null);
            offers.closePending(id, OfferStatus.EXPIRED, now);
            return 1;
        });
        return n == null ? 0 : n;
    }

    private static int safely(UUID orderId, Supplier<Integer> action) {
        try {
            Integer n = action.get();
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.error("Order maintenance failed for order {}", orderId, e);
            return 0;
        }
    }
}
