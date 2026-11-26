package uz.usta.platform.notification.internal.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.notification.internal.persistence.NotificationStore;
import uz.usta.platform.shared.outbox.OutboxMessage;

/** O'chirilgan hisobning bildirishnomalari va push tokenlari. */
@Component
class DeletedUserNotificationEraser {

    private final NotificationStore store;
    private final JsonMapper json;

    DeletedUserNotificationEraser(NotificationStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    /**
     * Keyingi eventlar (masalan buyurtma bekor bo'lishi) yozishi mumkin bo'lgan bildirishnomalarda shaxsiy
     * ma'lumot yo'q va push tokeni qolmagani uchun hech qayerga yuborilmaydi.
     */
    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        store.deleteAllFor(json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId());
    }
}
