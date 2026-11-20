package uz.usta.platform.order.internal.application;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;

/** Admin: buyurtmalarni ko'rish va nizolarni (DISPUTED) hal qilish. */
@Service
public class AdminOrderService {

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final OrderTransitions transitions;
    private final OrderAccess access;
    private final Commissions commissions;
    private final AuditLog audit;
    private final Clock clock;

    AdminOrderService(OrderRepository orders, OrderOfferRepository offers, OrderTransitions transitions,
                      OrderAccess access, Commissions commissions, AuditLog audit, Clock clock) {
        this.orders = orders;
        this.offers = offers;
        this.transitions = transitions;
        this.access = access;
        this.commissions = commissions;
        this.audit = audit;
        this.clock = clock;
    }

    public record Filter(OrderStatus status, UUID customerId, UUID masterId, UUID serviceId) {
    }

    public enum Resolution { COMPLETED, CANCELLED }

    @Transactional(readOnly = true)
    public Page<Order> search(Filter f, Pageable pageable) {
        Specification<Order> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.customerId() != null) {
                p.add(cb.equal(root.get("customerId"), f.customerId()));
            }
            if (f.masterId() != null) {
                p.add(cb.equal(root.get("masterId"), f.masterId()));
            }
            if (f.serviceId() != null) {
                p.add(cb.equal(root.get("serviceId"), f.serviceId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return orders.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Order get(UUID orderId) {
        return access.get(orderId);
    }

    /**
     * Nizoni hal qilish. COMPLETED — ish bajarilgan deb topildi (narx kiritilmagan bo'lsa {@code price} majburiy),
     * komissiya hisoblanadi va buyurtma COMMISSION'ga o'tadi. CANCELLED — buyurtma bekor qilinadi.
     */
    @Transactional
    public Order resolveDispute(UUID adminId, UUID orderId, Resolution resolution, BigDecimal price, String note) {
        Order order = access.lock(orderId);
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, order.getStatus().name(),
                    resolution.name());
        }
        if (resolution == Resolution.COMPLETED) {
            if (price != null) {
                order.setFinalPrice(price);
            } else if (order.getPrice() == null) {
                throw new BusinessException(OrderErrorCode.PRICE_REQUIRED);
            }
            transitions.change(order, OrderStatus.COMPLETED, adminId, note);
            MasterOrderService.chargeCommission(order, transitions, commissions);
        } else {
            order.recordCancellation(adminId, note);
            order.recordCommission(null);
            transitions.change(order, OrderStatus.CANCELLED, adminId, note);
            commissions.reverse(orderId, adminId); // komissiya hisoblangan bo'lsa — teskari yozuv
            offers.closePending(orderId, OfferStatus.CANCELLED, Instant.now(clock));
        }
        audit.record(adminId, "order.dispute-resolved", "order", orderId,
                Map.of("resolution", resolution.name(), "note", note == null ? "" : note));
        return order;
    }
}
