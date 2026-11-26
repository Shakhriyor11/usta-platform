package uz.usta.platform.complaint.internal.application;

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
import uz.usta.platform.complaint.ComplaintEvents;
import uz.usta.platform.complaint.internal.domain.Complaint;
import uz.usta.platform.complaint.internal.domain.ComplaintErrorCode;
import uz.usta.platform.complaint.internal.domain.ComplaintReason;
import uz.usta.platform.complaint.internal.domain.ComplaintStatus;
import uz.usta.platform.complaint.internal.persistence.ComplaintRepository;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.order.OrderApi.DisputeResolution;
import uz.usta.platform.order.OrderApi.Participants;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.shared.ratelimit.RateLimiter;

/**
 * Shikoyatlar. Mijoz ustadan, usta mijozdan shikoyat qila oladi (usta biriktirilgan buyurtma bo'yicha).
 * {@code dispute=true} bo'lsa — shu tranzaksiyada buyurtma DISPUTED ga o'tadi (to'lov va baholash to'xtaydi),
 * admin shikoyatni hal qilganda buyurtma bo'yicha ham qaror qabul qilinadi.
 */
@Service
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final OrderApi orders;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final RateLimiter rateLimiter;
    private final ComplaintProperties props;
    private final Clock clock;

    ComplaintService(ComplaintRepository complaints, OrderApi orders, OutboxPublisher outbox, AuditLog audit,
                     RateLimiter rateLimiter, ComplaintProperties props, Clock clock) {
        this.complaints = complaints;
        this.orders = orders;
        this.outbox = outbox;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.clock = clock;
    }

    public record Filter(ComplaintStatus status, UUID orderId, UUID againstUserId, UUID authorId) {
    }

    public record AdminView(Complaint complaint, String orderStatus, long complaintsAgainstUser) {
    }

    @Transactional
    public Complaint file(UUID authorId, UUID orderId, ComplaintReason reason, String description, boolean dispute) {
        Participants p = orders.participants(orderId);
        if (!p.isParticipant(authorId)) {
            throw BusinessException.notFound("Order", orderId);
        }
        if (p.masterId() == null) {
            throw new BusinessException(ComplaintErrorCode.NOT_ALLOWED, p.status());
        }
        if (complaints.hasOpen(orderId, authorId)) {
            throw new BusinessException(ComplaintErrorCode.ALREADY_OPEN);
        }
        UUID against = authorId.equals(p.customerId()) ? p.masterId() : p.customerId();
        rateLimiter.consume("complaint-create", authorId.toString(), props.createLimit(), props.createWindow());
        if (dispute) {
            orders.openDispute(orderId, authorId, OrderApi.COMPLAINT_REASON_PREFIX + reason.name());
        }
        Complaint c = complaints.saveAndFlush(new Complaint(orderId, authorId, against, reason, description.strip(),
                dispute));
        outbox.publish(ComplaintEvents.AGGREGATE, c.getId(), ComplaintEvents.COMPLAINT_FILED,
                new ComplaintEvents.ComplaintFiled(c.getId(), orderId, authorId, against, reason.name(), dispute));
        return c;
    }

    @Transactional(readOnly = true)
    public Page<Complaint> mine(UUID authorId, Pageable pageable) {
        return complaints.findByAuthorId(authorId, pageable);
    }

    @Transactional(readOnly = true)
    public Complaint mine(UUID authorId, UUID id) {
        return complaints.findById(id).filter(c -> c.getAuthorId().equals(authorId))
                .orElseThrow(() -> BusinessException.notFound("Complaint", id));
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public Page<Complaint> search(Filter f, Pageable pageable) {
        Specification<Complaint> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.orderId() != null) {
                p.add(cb.equal(root.get("orderId"), f.orderId()));
            }
            if (f.againstUserId() != null) {
                p.add(cb.equal(root.get("againstUserId"), f.againstUserId()));
            }
            if (f.authorId() != null) {
                p.add(cb.equal(root.get("authorId"), f.authorId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return complaints.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public AdminView adminView(UUID id) {
        Complaint c = get(id);
        return new AdminView(c, orders.participants(c.getOrderId()).status(),
                complaints.countByAgainstUserId(c.getAgainstUserId()));
    }

    @Transactional
    public Complaint take(UUID adminId, UUID id) {
        Complaint c = get(id);
        c.take(adminId);
        audit.record(adminId, "complaint.take", "complaint", id, Map.of());
        return complaints.save(c);
    }

    /**
     * Shikoyatni yopish. Nizo ochilgan va buyurtma hali DISPUTED bo'lsa — {@code orderResolution} majburiy:
     * COMPLETED (narx bilan, komissiya qayta hisoblanadi) yoki CANCELLED (komissiya qaytariladi).
     */
    @Transactional
    public Complaint close(UUID adminId, UUID id, ComplaintStatus result, String note,
                           DisputeResolution orderResolution, BigDecimal price) {
        Complaint c = get(id);
        if (!c.getStatus().isOpen()) {
            throw new BusinessException(ComplaintErrorCode.INVALID_STATE, c.getStatus().name());
        }
        if (c.isDispute() && "DISPUTED".equals(orders.participants(c.getOrderId()).status())) {
            if (orderResolution == null) {
                throw new BusinessException(ComplaintErrorCode.ORDER_RESOLUTION_REQUIRED);
            }
            orders.resolveDispute(adminId, c.getOrderId(), orderResolution, price, note);
        }
        c.close(result, adminId, note, Instant.now(clock));
        complaints.save(c);
        audit.record(adminId, "complaint." + result.name().toLowerCase(), "complaint", id,
                Map.of("orderResolution", orderResolution == null ? "" : orderResolution.name()));
        outbox.publish(ComplaintEvents.AGGREGATE, c.getId(), ComplaintEvents.COMPLAINT_CLOSED,
                new ComplaintEvents.ComplaintClosed(c.getId(), c.getOrderId(), c.getAuthorId(), c.getAgainstUserId(),
                        result.name()));
        return c;
    }

    private Complaint get(UUID id) {
        return complaints.findById(id).orElseThrow(() -> BusinessException.notFound("Complaint", id));
    }
}
