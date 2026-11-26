package uz.usta.platform.notification.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.chat.ChatEvents;
import uz.usta.platform.complaint.ComplaintEvents;
import uz.usta.platform.order.OrderEvents;
import uz.usta.platform.payment.PaymentEvents;
import uz.usta.platform.review.ReviewEvents;
import uz.usta.platform.shared.outbox.OutboxMessage;

/**
 * Order va review eventlarini bildirishnomalarga aylantiradi. Kim nimani oladi:
 * <ul>
 *   <li>yangi taklif -> usta;</li>
 *   <li>ACCEPTED, IN_PROGRESS, COMPLETED, EXPIRED -> mijoz;</li>
 *   <li>CANCELLED, DISPUTED -> amalni bajarmagan tomon(lar);</li>
 *   <li>nizo hal qilindi (DISPUTED -> ...) -> ikkala tomon;</li>
 *   <li>yangi baho -> usta.</li>
 * </ul>
 * Idempotent: {@code (source_event_id, user_id)} UNIQUE.
 */
@Component
class DomainEventsListener {

    private final NotificationService notifications;
    private final JsonMapper json;

    DomainEventsListener(NotificationService notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + OrderEvents.OFFER_CREATED + "'")
    @Transactional
    public void onOffer(OutboxMessage m) {
        OrderEvents.OfferCreated e = json.readValue(m.payload(), OrderEvents.OfferCreated.class);
        long minutes = Math.max(1, Duration.between(m.createdAt(), e.expiresAt()).toMinutes());
        String km = BigDecimal.valueOf(e.distanceMeters() / 1000d).setScale(1, RoundingMode.HALF_UP).toPlainString();
        notifications.notify(m.id(), e.masterId(), NotificationType.NEW_OFFER, List.of(km, String.valueOf(minutes)),
                data(e.orderId()));
    }

    @EventListener(condition = "#m.eventType == '" + OrderEvents.STATUS_CHANGED + "'")
    @Transactional
    public void onStatus(OutboxMessage m) {
        OrderEvents.StatusChanged e = json.readValue(m.payload(), OrderEvents.StatusChanged.class);
        Map<String, String> data = data(e.orderId());
        if ("DISPUTED".equals(e.from())) {
            notifyAll(m, Arrays.asList(e.customerId(), e.masterId()), null, NotificationType.DISPUTE_RESOLVED,
                    List.of(), data);
            return;
        }
        switch (e.to()) {
            case "ACCEPTED" -> notifications.notify(m.id(), e.customerId(), NotificationType.ORDER_ACCEPTED, List.of(), data);
            case "IN_PROGRESS" -> notifications.notify(m.id(), e.customerId(), NotificationType.ORDER_STARTED, List.of(), data);
            // mijoz yakuniy summani darhol ko'radi — rozi bo'lmasa nizo ochadi
            case "COMPLETED" -> notifications.notify(m.id(), e.customerId(), NotificationType.ORDER_COMPLETED,
                    e.price() == null ? List.of() : List.of(e.price().toPlainString()), data);
            case "SEARCHING" -> {
                if ("ACCEPTED".equals(e.from())) { // usta voz kechdi
                    notifications.notify(m.id(), e.customerId(), NotificationType.ORDER_REOPENED, List.of(), data);
                }
            }
            case "EXPIRED" -> notifications.notify(m.id(), e.customerId(), NotificationType.ORDER_EXPIRED, List.of(), data);
            case "CANCELLED" -> notifyAll(m, Arrays.asList(e.customerId(), e.masterId()), e.actorId(),
                    NotificationType.ORDER_CANCELLED, List.of(), data);
            case "DISPUTED" -> notifyAll(m, Arrays.asList(e.customerId(), e.masterId()), e.actorId(),
                    NotificationType.ORDER_DISPUTED, List.of(), data);
            default -> {
                // CREATED, COMMISSION, REVIEWED — alohida bildirishnoma kerak emas
            }
        }
    }

    @EventListener(condition = "#m.eventType == '" + ReviewEvents.REVIEW_CREATED + "'")
    @Transactional
    public void onReview(OutboxMessage m) {
        ReviewEvents.ReviewCreated e = json.readValue(m.payload(), ReviewEvents.ReviewCreated.class);
        notifications.notify(m.id(), e.masterId(), NotificationType.NEW_REVIEW, List.of(String.valueOf(e.rating())),
                data(e.orderId()));
    }

    @EventListener(condition = "#m.eventType == '" + PaymentEvents.COMMISSION_INVOICED + "'")
    @Transactional
    public void onInvoiced(OutboxMessage m) {
        PaymentEvents.CommissionInvoiced e = json.readValue(m.payload(), PaymentEvents.CommissionInvoiced.class);
        notifications.notify(m.id(), e.masterId(), NotificationType.COMMISSION_INVOICED,
                List.of(e.amount().toPlainString(), String.valueOf(e.orders()), e.dueDate().toString()),
                Map.of("period", e.period().toString()));
    }

    @EventListener(condition = "#m.eventType == '" + PaymentEvents.COMMISSION_SETTLED + "'")
    @Transactional
    public void onSettled(OutboxMessage m) {
        PaymentEvents.CommissionSettled e = json.readValue(m.payload(), PaymentEvents.CommissionSettled.class);
        notifications.notify(m.id(), e.masterId(), NotificationType.COMMISSION_SETTLED,
                List.of(e.amount().toPlainString(), e.period().toString()), Map.of("period", e.period().toString()));
    }

    @EventListener(condition = "#m.eventType == '" + ChatEvents.MESSAGE_SENT + "'")
    public void onChatMessage(OutboxMessage m) {
        ChatEvents.MessageSent e = json.readValue(m.payload(), ChatEvents.MessageSent.class);
        notifications.pushOnly(e.recipientId(), NotificationType.CHAT_MESSAGE, List.of(e.preview()),
                Map.of("orderId", e.orderId().toString(), "screen", "chat"));
    }

    @EventListener(condition = "#m.eventType == '" + ComplaintEvents.COMPLAINT_CLOSED + "'")
    @Transactional
    public void onComplaintClosed(OutboxMessage m) {
        ComplaintEvents.ComplaintClosed e = json.readValue(m.payload(), ComplaintEvents.ComplaintClosed.class);
        NotificationType type = "RESOLVED".equals(e.status()) ? NotificationType.COMPLAINT_RESOLVED
                : NotificationType.COMPLAINT_REJECTED;
        notifications.notify(m.id(), e.authorId(), type, List.of(),
                Map.of("orderId", e.orderId().toString(), "complaintId", e.complaintId().toString()));
    }

    private void notifyAll(OutboxMessage m, List<UUID> recipients, UUID except, NotificationType type,
                           List<String> args, Map<String, String> data) {
        recipients.stream().filter(id -> id != null && !id.equals(except))
                .forEach(id -> notifications.notify(m.id(), id, type, args, data));
    }

    private static Map<String, String> data(UUID orderId) {
        return Map.of("orderId", orderId.toString());
    }
}
