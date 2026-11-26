package uz.usta.platform.order.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderAttachment;
import uz.usta.platform.order.internal.domain.OrderErrorCode;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.persistence.OrderAttachmentRepository;
import uz.usta.platform.order.internal.persistence.OrderOfferRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.profile.CustomerDirectory;
import uz.usta.platform.search.MasterMatcher;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.ratelimit.RateLimiter;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;

/** Mijoz amallari: buyurtma yaratish, kuzatish, bekor qilish, biriktirmalar, nizo. */
@Service
public class CustomerOrderService {

    private final OrderRepository orders;
    private final OrderOfferRepository offers;
    private final OrderAttachmentRepository attachments;
    private final OrderTransitions transitions;
    private final OfferDispatcher dispatcher;
    private final OrderAccess access;
    private final CatalogApi catalog;
    private final CustomerDirectory customers;
    private final MasterMatcher matcher;
    private final Commissions commissions;
    private final FileStorage storage;
    private final RateLimiter rateLimiter;
    private final OrderProperties props;
    private final Clock clock;

    CustomerOrderService(OrderRepository orders, OrderOfferRepository offers, OrderAttachmentRepository attachments,
                         OrderTransitions transitions, OfferDispatcher dispatcher, OrderAccess access,
                         CatalogApi catalog, CustomerDirectory customers, MasterMatcher matcher,
                         Commissions commissions, FileStorage storage, RateLimiter rateLimiter, OrderProperties props,
                         Clock clock) {
        this.orders = orders;
        this.offers = offers;
        this.attachments = attachments;
        this.transitions = transitions;
        this.dispatcher = dispatcher;
        this.access = access;
        this.catalog = catalog;
        this.customers = customers;
        this.matcher = matcher;
        this.commissions = commissions;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.clock = clock;
    }

    /** Manzil: saqlangan ({@code addressId}) yoki shu buyurtma uchun kiritilgan. */
    public record InlineAddress(String address, String details, double latitude, double longitude) {
    }

    public record CreateCommand(UUID serviceId, String description, UUID addressId, InlineAddress address,
                                Instant scheduledAt, UUID preferredMasterId) {
    }

    /**
     * Buyurtma yaratadi (CREATED), darhol qidiruvga o'tkazadi (SEARCHING) va mos ustalarga taklif yuboradi.
     * Mos usta hozir topilmasa ham buyurtma SEARCHING'da qoladi — fon vazifasi qayta urinadi.
     */
    @Transactional
    public Order create(UUID customerId, CreateCommand cmd) {
        Instant now = Instant.now(clock);
        catalog.requireActiveService(cmd.serviceId());
        if (cmd.scheduledAt() != null && (cmd.scheduledAt().isBefore(now.minusSeconds(60))
                || cmd.scheduledAt().isAfter(now.plus(props.maxScheduleAhead())))) {
            throw new BusinessException(OrderErrorCode.INVALID_SCHEDULE, props.maxScheduleAhead().toDays());
        }
        String title;
        String text;
        String details;
        double lat;
        double lng;
        if (cmd.addressId() != null) {
            CustomerDirectory.AddressSnapshot a = customers.address(customerId, cmd.addressId());
            title = a.title();
            text = a.address();
            details = a.details();
            lat = a.latitude();
            lng = a.longitude();
        } else if (cmd.address() != null) {
            title = null;
            text = cmd.address().address();
            details = cmd.address().details();
            lat = cmd.address().latitude();
            lng = cmd.address().longitude();
        } else {
            throw new BusinessException(OrderErrorCode.ADDRESS_REQUIRED);
        }
        if (cmd.preferredMasterId() != null) {
            if (cmd.preferredMasterId().equals(customerId)) {
                throw new BusinessException(OrderErrorCode.CANNOT_ORDER_FROM_SELF);
            }
            if (matcher.match(cmd.preferredMasterId(), cmd.serviceId(), lat, lng).isEmpty()
                    || commissions.hasOverdueDebt(cmd.preferredMasterId())) {
                throw new BusinessException(OrderErrorCode.MASTER_NOT_AVAILABLE);
            }
        }
        // faqat haqiqatan yaratiladigan buyurtmalar hisoblanadi (validatsiya xatolari limitni yemaydi)
        rateLimiter.consume("order-create", customerId.toString(), props.createLimit(), props.createWindow());
        Order order = transitions.created(new Order(customerId, cmd.serviceId(), cmd.preferredMasterId(),
                cmd.description().strip(), title, text, details, lat, lng, cmd.scheduledAt(),
                props.searchDeadline(now, cmd.scheduledAt())), customerId);
        transitions.change(order, OrderStatus.SEARCHING, customerId, null);
        dispatcher.dispatch(order);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> list(UUID customerId, Collection<OrderStatus> statuses, Pageable pageable) {
        return statuses == null || statuses.isEmpty()
                ? orders.findByCustomerId(customerId, pageable)
                : orders.findByCustomerIdAndStatusIn(customerId, statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Order get(UUID customerId, UUID orderId) {
        Order order = access.get(orderId);
        if (!order.isOwnedBy(customerId)) {
            throw OrderAccess.notFound(orderId);
        }
        return order;
    }

    /** Usta ishni boshlaguncha (CREATED/SEARCHING/ACCEPTED) bekor qilish mumkin. */
    @Transactional
    public Order cancel(UUID customerId, UUID orderId, String reason) {
        Order order = access.lockForCustomer(orderId, customerId);
        if (!OrderStatus.CUSTOMER_CANCELLABLE.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION, order.getStatus().name(),
                    OrderStatus.CANCELLED.name());
        }
        order.recordCancellation(customerId, reason);
        transitions.change(order, OrderStatus.CANCELLED, customerId, reason);
        offers.closePending(orderId, OfferStatus.CANCELLED, Instant.now(clock));
        return order;
    }

    @Transactional
    public Order openDispute(UUID customerId, UUID orderId, String reason) {
        Order order = access.lockForCustomer(orderId, customerId);
        return Disputes.open(order, customerId, reason, transitions, commissions);
    }

    /**
     * Fayl DB tranzaksiyasi va buyurtma qulfi tashqarisida yuklanadi (sekin yuklash ulanish va qulfni band
     * qilmaydi); yozuv esa qulf ostida, shartlar qayta tekshirilib saqlanadi.
     */
    public OrderAttachment addAttachment(UUID customerId, UUID orderId, MultipartFile file) {
        requireAttachable(get(customerId, orderId)); // yuklashdan oldin tez rad etish
        return storage.storeAndSave(FilePurpose.ORDER_ATTACHMENT, orderId, file, stored -> {
            requireAttachable(access.lockForCustomer(orderId, customerId));
            return attachments.save(new OrderAttachment(orderId, stored.key(), stored.contentType(),
                    stored.sizeBytes()));
        });
    }

    @Transactional
    public void deleteAttachment(UUID customerId, UUID orderId, UUID attachmentId) {
        Order order = access.lockForCustomer(orderId, customerId);
        requireEditable(order);
        OrderAttachment a = attachments.findByIdAndOrderId(attachmentId, orderId)
                .orElseThrow(() -> BusinessException.notFound("Attachment", attachmentId));
        attachments.delete(a);
        storage.deleteAfterCommit(a.getFileKey());
    }

    private void requireAttachable(Order order) {
        requireEditable(order);
        if (attachments.countByOrderId(order.getId()) >= props.maxAttachments()) {
            throw new BusinessException(OrderErrorCode.ATTACHMENT_LIMIT_EXCEEDED, props.maxAttachments());
        }
    }

    private static void requireEditable(Order order) {
        if (!OrderStatus.EDITABLE.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_EDITABLE, order.getStatus().name());
        }
    }
}
