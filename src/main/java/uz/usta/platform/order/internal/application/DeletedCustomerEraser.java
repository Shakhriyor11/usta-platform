package uz.usta.platform.order.internal.application;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.order.internal.persistence.OrderAttachmentRepository;
import uz.usta.platform.order.internal.persistence.OrderRepository;
import uz.usta.platform.shared.outbox.OutboxMessage;
import uz.usta.platform.storage.FileStorage;

/**
 * O'chirilgan mijozning buyurtmalaridagi shaxsiy ma'lumotlar: manzil, tavsif va biriktirilgan rasm/videolar
 * o'chiriladi, koordinata ~1 km aniqlikkacha yaxlitlanadi. Narx, holat va tarix (hisob-kitob uchun) qoladi.
 */
@Component
class DeletedCustomerEraser {

    private final OrderRepository orders;
    private final OrderAttachmentRepository attachments;
    private final FileStorage storage;
    private final JsonMapper json;

    DeletedCustomerEraser(OrderRepository orders, OrderAttachmentRepository attachments, FileStorage storage,
                          JsonMapper json) {
        this.orders = orders;
        this.attachments = attachments;
        this.storage = storage;
        this.json = json;
    }

    /** {@code BlockedUserListener} bilan tartibga bog'liq emas: bulk yangilash oldidan flush, keyin clear. */
    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        UUID userId = json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId();
        attachments.findByCustomer(userId).forEach(a -> {
            storage.deleteAfterCommit(a.getFileKey());
            attachments.delete(a);
        });
        attachments.flush();
        orders.anonymizeCustomer(userId);
    }
}
