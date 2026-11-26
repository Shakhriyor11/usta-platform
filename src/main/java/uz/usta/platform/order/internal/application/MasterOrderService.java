package uz.usta.platform.order.internal.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderOffer;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.shared.error.BusinessException;

/** Usta amallari: takliflar, qabul/rad etish, ishni boshlash va yakunlash. */
@Service
public class MasterOrderService {

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final OrderTransitions transitions;
    private final OfferDispatcher dispatcher;
    private final OrderAccess access;
    private final MasterProfiles masters;
    private final CatalogApi catalog;
    private final Commissions commissions;
    private final OrderRequeue requeue;
    private final Clock clock;

    MasterOrderService(OrderRepository orders, OrderOfferRepository offers, OrderTransitions transitions,
                       OfferDispatcher dispatcher, OrderAccess access, MasterProfiles masters, CatalogApi catalog,
                       Commissions commissions, OrderRequeue requeue, Clock clock) {
        this.requeue = requeue;
        this.orders = orders;
        this.offers = offers;
        this.transitions = transitions;
        this.dispatcher = dispatcher;
        this.access = access;
        this.masters = masters;
        this.catalog = catalog;
        this.commissions = commissions;
        this.clock = clock;
    }

    public record OpenOffer(OrderOffer offer, Order order) {
    }

    @Transactional(readOnly = true)
    public List<OpenOffer> openOffers(UUID masterId) {
        List<OrderOffer> open = offers.findOpenForMaster(masterId, Instant.now(clock));
        Map<UUID, Order> byId = orders.findAllById(open.stream().map(OrderOffer::getOrderId).toList()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return open.stream().filter(f -> byId.containsKey(f.getOrderId()))
                .map(f -> new OpenOffer(f, byId.get(f.getOrderId()))).toList();
    }

    /**
     * Taklifni qabul qilish. Buyurtma qatori qulflanadi — parallel qabul qilishda ikkinchisi
     * {@code ORDER_ALREADY_TAKEN} oladi.
     */
    @Transactional
    public Order accept(UUID masterId, UUID orderId) {
        Instant now = Instant.now(clock);
        Order order = access.lock(orderId);
        OrderOffer offer = offers.findByOrderIdAndMasterId(orderId, masterId)
                .orElseThrow(() -> OrderAccess.notFound(orderId));
        if (order.getStatus() != OrderStatus.SEARCHING) {
            throw new BusinessException(order.getMasterId() != null ? OrderErrorCode.ORDER_ALREADY_TAKEN
                    : OrderErrorCode.OFFER_NOT_AVAILABLE);
        }
        if (!offer.isOpen(now)) {
            throw new BusinessException(OrderErrorCode.OFFER_NOT_AVAILABLE);
        }
        if (!masters.isPublic(masterId)) {
            throw new BusinessException(OrderErrorCode.MASTER_NOT_ACTIVE);
        }
        if (commissions.hasOverdueDebt(masterId)) {
            throw new BusinessException(OrderErrorCode.MASTER_HAS_OVERDUE_DEBT);
        }
        order.assignMaster(masterId, catalog.priceFrom(masterId, order.getServiceId()).orElse(null));
        offer.respond(OfferStatus.ACCEPTED, now);
        offers.save(offer);
        transitions.change(order, OrderStatus.ACCEPTED, masterId, null);
        offers.closePending(orderId, OfferStatus.CANCELLED, now);
        return order;
    }

    /** Rad etish: buyurtma SEARCHING'da qoladi; ochiq taklif qolmasa — darhol keyingi ustalarga yuboriladi. */
    @Transactional
    public void reject(UUID masterId, UUID orderId) {
        Instant now = Instant.now(clock);
        Order order = access.lock(orderId);
        OrderOffer offer = offers.findByOrderIdAndMasterId(orderId, masterId)
                .orElseThrow(() -> OrderAccess.notFound(orderId));
        if (!offer.isOpen(now) || order.getStatus() != OrderStatus.SEARCHING) {
            throw new BusinessException(OrderErrorCode.OFFER_NOT_AVAILABLE);
        }
        offer.respond(OfferStatus.REJECTED, now);
        offers.saveAndFlush(offer);
        dispatcher.dispatch(order);
    }

    @Transactional
    public Order start(UUID masterId, UUID orderId) {
        Order order = access.lockForMaster(orderId, masterId);
        transitions.change(order, OrderStatus.IN_PROGRESS, masterId, null);
        return order;
    }

    /** Ishni yakunlash: yakuniy narx kiritiladi, komissiya hisoblanadi (COMPLETED -> COMMISSION). */
    @Transactional
    public Order complete(UUID masterId, UUID orderId, BigDecimal finalPrice) {
        Order order = access.lockForMaster(orderId, masterId);
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, order.getStatus().name(),
                    OrderStatus.COMPLETED.name());
        }
        order.setFinalPrice(finalPrice);
        transitions.change(order, OrderStatus.COMPLETED, masterId, null);
        chargeCommission(order, transitions, commissions);
        return order;
    }

    /**
     * Usta qabul qilgan, lekin hali boshlamagan buyurtmadan voz kechishi (sabab majburiy): buyurtma qayta
     * qidiruvga o'tadi. Javobda usta endi biriktirilmagan — mijoz ma'lumotlari va aniq manzil yopiladi.
     */
    @Transactional
    public MasterView cancel(UUID masterId, UUID orderId, String reason) {
        Order order = access.lockForMaster(orderId, masterId);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, order.getStatus().name(),
                    OrderStatus.SEARCHING.name());
        }
        return new MasterView(order, requeue.requeue(order, masterId, reason));
    }

    @Transactional
    public Order openDispute(UUID masterId, UUID orderId, String reason) {
        return Disputes.open(access.lockForMaster(orderId, masterId), masterId, reason, transitions, commissions);
    }

    @Transactional(readOnly = true)
    public Page<Order> myOrders(UUID masterId, Collection<OrderStatus> statuses, Pageable pageable) {
        return statuses == null || statuses.isEmpty()
                ? orders.findByMasterId(masterId, pageable)
                : orders.findByMasterIdAndStatusIn(masterId, statuses, pageable);
    }

    /**
     * Usta ko'ra oladigan buyurtma: unga biriktirilgan yoki unda hali ochiq taklifi bor. Rad etilgan, muddati
     * o'tgan yoki boshqa usta olgan taklif buyurtmani ko'rishga huquq bermaydi.
     */
    @Transactional(readOnly = true)
    public MasterView view(UUID masterId, UUID orderId) {
        Order order = access.get(orderId);
        if (order.isAssignedTo(masterId)) {
            return new MasterView(order, null);
        }
        Instant now = Instant.now(clock);
        OrderOffer offer = offers.findByOrderIdAndMasterId(orderId, masterId)
                .filter(o -> order.getStatus() == OrderStatus.SEARCHING && o.isOpen(now))
                .orElseThrow(() -> OrderAccess.notFound(orderId));
        return new MasterView(order, offer);
    }

    /** @param offer usta hali biriktirilmagan bo'lsa — uning taklifi (aniq manzil va telefon ko'rsatilmaydi) */
    public record MasterView(Order order, OrderOffer offer) {
    }

    static void chargeCommission(Order order, OrderTransitions transitions, Commissions commissions) {
        order.recordCommission(commissions.charge(order.getId(), order.getMasterId(), order.getPrice()));
        transitions.change(order, OrderStatus.COMMISSION, null, null);
    }
}
